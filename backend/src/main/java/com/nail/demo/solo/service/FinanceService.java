package com.nail.demo.solo.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.nail.demo.exception.BusinessException;
import com.nail.demo.solo.common.SoloDepositFlowType;
import com.nail.demo.solo.common.SoloMoneyCategory;
import com.nail.demo.solo.common.SoloMoneyDirection;
import com.nail.demo.solo.dto.DepositResponse;
import com.nail.demo.solo.dto.FinanceSummaryResponse;
import com.nail.demo.solo.dto.MoneyFlowRequest;
import com.nail.demo.solo.dto.MoneyFlowResponse;
import com.nail.demo.solo.entity.Booking;
import com.nail.demo.solo.entity.Client;
import com.nail.demo.solo.entity.MoneyFlow;
import com.nail.demo.solo.repository.BookingRepository;
import com.nail.demo.solo.repository.ClientRepository;
import com.nail.demo.solo.repository.DepositFlowRepository;
import com.nail.demo.solo.repository.MoneyFlowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 财务：月度概览 + 三本明细（收入 / 支出 / 定金）。
 *
 * <p>对账口径：聚合查询结果与明细逐笔累加必须一致，不一致就把 balanced 置 false。
 */
@Service
@RequiredArgsConstructor
public class FinanceService {

    private final MoneyFlowRepository moneyFlowRepository;
    private final DepositFlowRepository depositFlowRepository;
    private final DepositService depositService;
    private final BookingRepository bookingRepository;
    private final ClientRepository clientRepository;

    @Transactional(readOnly = true)
    public FinanceSummaryResponse summary(String month) {
        YearMonth yearMonth = resolveMonth(month);
        LocalDateTime from = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime to = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        BigDecimal income = amountOf(moneyFlowRepository.sumByDirection(SoloMoneyDirection.IN, from, to));
        BigDecimal expense = amountOf(moneyFlowRepository.sumByDirection(SoloMoneyDirection.OUT, from, to));

        Map<String, BigDecimal> depositSums = depositFlowRepository.sumByType(from, to).stream()
                .collect(Collectors.toMap(row -> String.valueOf(row[0]), row -> amountOf(row[1])));

        List<MoneyFlow> details = moneyFlowRepository
                .findByHappenedAtBetweenOrderByHappenedAtAsc(from, to);
        BigDecimal detailIncome = sumByDirection(details, SoloMoneyDirection.IN);
        BigDecimal detailExpense = sumByDirection(details, SoloMoneyDirection.OUT);

        return FinanceSummaryResponse.builder()
                .month(yearMonth.toString())
                .income(income)
                .expense(expense)
                .net(income.subtract(expense))
                .depositCollected(depositSums.getOrDefault(SoloDepositFlowType.COLLECT, BigDecimal.ZERO))
                .depositRefunded(depositSums.getOrDefault(SoloDepositFlowType.REFUND, BigDecimal.ZERO))
                .depositForfeited(depositSums.getOrDefault(SoloDepositFlowType.FORFEIT, BigDecimal.ZERO))
                .detailIncome(detailIncome)
                .detailExpense(detailExpense)
                .balanced(income.compareTo(detailIncome) == 0 && expense.compareTo(detailExpense) == 0)
                .build();
    }

    /**
     * 收入 / 支出明细。direction 传 IN 或 OUT，不传返回全部。
     */
    @Transactional(readOnly = true)
    public List<MoneyFlowResponse> flows(String month, String direction) {
        YearMonth yearMonth = resolveMonth(month);
        LocalDateTime from = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime to = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        List<MoneyFlow> flows = StringUtils.hasText(direction)
                ? moneyFlowRepository.findByDirectionAndHappenedAtBetweenOrderByHappenedAtAsc(
                        normalizeDirection(direction), from, to)
                : moneyFlowRepository.findByHappenedAtBetweenOrderByHappenedAtAsc(from, to);

        Map<Long, Booking> bookings = bookingRepository.findAllById(
                        flows.stream().map(MoneyFlow::getBookingId)
                                .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Booking::getId, Function.identity()));
        Map<Long, Client> clients = clientRepository.findAllById(
                        bookings.values().stream().map(Booking::getClientId).distinct().toList())
                .stream().collect(Collectors.toMap(Client::getId, Function.identity()));

        return flows.stream().map(flow -> {
            Booking booking = flow.getBookingId() == null ? null : bookings.get(flow.getBookingId());
            Client client = booking == null ? null : clients.get(booking.getClientId());
            return MoneyFlowResponse.builder()
                    .id(flow.getId())
                    .direction(flow.getDirection())
                    .category(flow.getCategory())
                    .amount(flow.getAmount())
                    .happenedAt(flow.getHappenedAt())
                    .bookingId(flow.getBookingId())
                    .clientCode(client == null ? null : client.getCode())
                    .clientNickname(client == null ? null : client.getNickname())
                    .remark(flow.getRemark())
                    .build();
        }).toList();
    }

    /** 定金明细（第三本账）。 */
    @Transactional(readOnly = true)
    public List<DepositResponse> deposits(String month) {
        YearMonth yearMonth = resolveMonth(month);
        return depositService.flows(yearMonth.atDay(1).atStartOfDay(), yearMonth.plusMonths(1).atDay(1).atStartOfDay());
    }

    /** 记一笔收支。物料支出、尾款收入都走这里。 */
    @Transactional
    public MoneyFlowResponse record(MoneyFlowRequest request) {
        MoneyFlow flow = new MoneyFlow();
        flow.setDirection(normalizeDirection(request.getDirection()));
        flow.setCategory(normalizeCategory(request.getCategory()));
        flow.setAmount(request.getAmount());
        flow.setHappenedAt(request.getHappenedAt() == null ? LocalDateTime.now() : request.getHappenedAt());
        flow.setBookingId(request.getBookingId());
        flow.setRemark(request.getRemark());
        MoneyFlow saved = moneyFlowRepository.save(flow);

        return MoneyFlowResponse.builder()
                .id(saved.getId())
                .direction(saved.getDirection())
                .category(saved.getCategory())
                .amount(saved.getAmount())
                .happenedAt(saved.getHappenedAt())
                .bookingId(saved.getBookingId())
                .remark(saved.getRemark())
                .build();
    }

    private BigDecimal sumByDirection(List<MoneyFlow> flows, String direction) {
        return flows.stream()
                .filter(flow -> direction.equals(flow.getDirection()))
                .map(flow -> amountOf(flow.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal amountOf(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private YearMonth resolveMonth(String month) {
        if (!StringUtils.hasText(month)) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException("月份格式应为 YYYY-MM，例如 2026-11");
        }
    }

    private String normalizeDirection(String direction) {
        String normalized = direction.trim().toUpperCase(Locale.ROOT);
        if (!SoloMoneyDirection.IN.equals(normalized) && !SoloMoneyDirection.OUT.equals(normalized)) {
            throw new BusinessException("收支方向只能是 IN 或 OUT");
        }
        return normalized;
    }

    private String normalizeCategory(String category) {
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case SoloMoneyCategory.SERVICE, SoloMoneyCategory.MATERIAL, SoloMoneyCategory.OTHER -> normalized;
            default -> throw new BusinessException("收支类别只能是 SERVICE / MATERIAL / OTHER");
        };
    }
}

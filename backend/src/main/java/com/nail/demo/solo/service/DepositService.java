package com.nail.demo.solo.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.nail.demo.exception.BusinessException;
import com.nail.demo.solo.common.SoloBookingEventAction;
import com.nail.demo.solo.common.SoloBookingStatus;
import com.nail.demo.solo.common.SoloChangeDepositAction;
import com.nail.demo.solo.common.SoloDepositFlowType;
import com.nail.demo.solo.dto.DepositRequest;
import com.nail.demo.solo.dto.DepositResponse;
import com.nail.demo.solo.dto.DepositSummaryResponse;
import com.nail.demo.solo.entity.Booking;
import com.nail.demo.solo.entity.DepositFlow;
import com.nail.demo.solo.repository.BookingRepository;
import com.nail.demo.solo.repository.DepositFlowRepository;
import com.nail.demo.solo.support.DepositLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 定金四条流水。金额只落流水，状态由 {@link DepositLedger} 推导。
 */
@Service
@RequiredArgsConstructor
public class DepositService {

    /** 定金固定 30 元；P7 设置页接配置后再放开。 */
    public static final BigDecimal STANDARD_AMOUNT = new BigDecimal("30");

    private static final Map<String, String> LABELS = Map.of(
            SoloDepositFlowType.COLLECT, "收取",
            SoloDepositFlowType.OFFSET, "抵扣",
            SoloDepositFlowType.REFUND, "退还",
            SoloDepositFlowType.FORFEIT, "没收");

    private final DepositFlowRepository depositFlowRepository;
    private final BookingRepository bookingRepository;
    private final BookingGuard guard;

    @Transactional(readOnly = true)
    public DepositSummaryResponse summary(Long bookingId) {
        guard.requireBooking(bookingId);
        return toSummary(bookingId, depositFlowRepository.findByBookingIdOrderByHappenedAtAsc(bookingId));
    }

    /**
     * 记一条定金流水。
     *
     * <p>收取时：定金固定 30，累计不得超过 30；若预约还在「待定金」，收定即改判为「已定档」，
     * 并在这里补做一次冲突校验——待定金状态本身不占档，不能带冲突定档。
     */
    @Transactional
    public DepositSummaryResponse add(Long bookingId, DepositRequest request) {
        Booking booking = guard.requireBooking(bookingId);
        String type = normalizeType(request.getType());
        DepositLedger ledger = ledgerOf(bookingId);
        BigDecimal amount = resolveAmount(type, request.getAmount(), ledger);

        if (amount.signum() <= 0) {
            throw new BusinessException("定金金额必须大于 0");
        }

        if (SoloDepositFlowType.COLLECT.equals(type)) {
            if (ledger.collected().add(amount).compareTo(STANDARD_AMOUNT) > 0) {
                throw new BusinessException("定金固定 %s 元，已收 %s 元".formatted(
                        STANDARD_AMOUNT.toPlainString(), ledger.collected().toPlainString()));
            }
            if (SoloBookingStatus.WAIT_DEPOSIT.equals(booking.getStatus())) {
                guard.assertNoConflict(booking.getStartAt(), booking.getEndAt(), bookingId);
            }
        } else {
            if (ledger.collected().signum() == 0) {
                throw new BusinessException("这单还没收定金，不能" + LABELS.get(type));
            }
            if (amount.compareTo(ledger.balance()) > 0) {
                throw new BusinessException("可动用的定金余额只有 %s 元".formatted(ledger.balance().toPlainString()));
            }
        }

        saveFlow(bookingId, type, amount, request.getReason());

        if (SoloDepositFlowType.COLLECT.equals(type)
                && SoloBookingStatus.WAIT_DEPOSIT.equals(booking.getStatus())) {
            booking.setStatus(SoloBookingStatus.BOOKED);
            bookingRepository.save(booking);
            guard.recordEvent(bookingId, SoloBookingEventAction.CONFIRM,
                    SoloBookingStatus.WAIT_DEPOSIT, SoloBookingStatus.BOOKED, "收取定金后定档");
        }

        return summary(bookingId);
    }

    /**
     * 改期 / 取消时按老板的选择处理定金。余额已结清或未收过定金时跳过，不阻塞主流程。
     *
     * @return 是否真的写了一条流水
     */
    @Transactional
    public boolean applyDecision(Long bookingId, String decision, String reason) {
        String action = normalizeDecision(decision);
        if (SoloChangeDepositAction.NONE.equals(action) || SoloChangeDepositAction.FREE.equals(action)) {
            return false;
        }
        DepositLedger ledger = ledgerOf(bookingId);
        if (ledger.collected().signum() == 0 || ledger.balance().signum() <= 0) {
            return false;
        }
        saveFlow(bookingId, action, ledger.balance(), reason);
        return true;
    }

    @Transactional(readOnly = true)
    public List<DepositResponse> flows(LocalDateTime from, LocalDateTime to) {
        return depositFlowRepository.findByHappenedAtBetweenOrderByHappenedAtAsc(from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    public String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            throw new BusinessException("定金流水类型不能为空");
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!LABELS.containsKey(normalized)) {
            throw new BusinessException("未知的定金类型：" + type);
        }
        return normalized;
    }

    /** 处理选择：NONE / FREE / REFUND / FORFEIT。 */
    public String normalizeDecision(String decision) {
        if (decision == null || decision.isBlank()) {
            return SoloChangeDepositAction.NONE;
        }
        String normalized = decision.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case SoloChangeDepositAction.NONE, SoloChangeDepositAction.FREE,
                 SoloChangeDepositAction.REFUND, SoloChangeDepositAction.FORFEIT -> normalized;
            default -> throw new BusinessException("未知的定金处理方式：" + decision);
        };
    }

    public DepositLedger ledgerOf(Long bookingId) {
        return DepositLedger.of(depositFlowRepository.findByBookingIdOrderByHappenedAtAsc(bookingId));
    }

    private BigDecimal resolveAmount(String type, BigDecimal requested, DepositLedger ledger) {
        if (requested != null) {
            return requested;
        }
        if (SoloDepositFlowType.COLLECT.equals(type)) {
            return STANDARD_AMOUNT;
        }
        return ledger.balance();
    }

    private void saveFlow(Long bookingId, String type, BigDecimal amount, String reason) {
        DepositFlow flow = new DepositFlow();
        flow.setBookingId(bookingId);
        flow.setType(type);
        flow.setAmount(amount);
        flow.setHappenedAt(LocalDateTime.now());
        flow.setReason(reason);
        depositFlowRepository.save(flow);
    }

    private DepositSummaryResponse toSummary(Long bookingId, List<DepositFlow> flows) {
        DepositLedger ledger = DepositLedger.of(flows);
        return DepositSummaryResponse.builder()
                .bookingId(bookingId)
                .collected(ledger.collected())
                .offset(ledger.offset())
                .refunded(ledger.refunded())
                .forfeited(ledger.forfeited())
                .balance(ledger.balance())
                .state(ledger.state())
                .flows(flows.stream().map(this::toResponse).toList())
                .build();
    }

    private DepositResponse toResponse(DepositFlow flow) {
        return DepositResponse.builder()
                .id(flow.getId())
                .bookingId(flow.getBookingId())
                .type(flow.getType())
                .amount(flow.getAmount())
                .happenedAt(flow.getHappenedAt())
                .reason(flow.getReason())
                .build();
    }

    /** 批量取一列表的定金台账，排期展示用。 */
    public Map<Long, DepositLedger> ledgersOf(List<Long> bookingIds) {
        if (bookingIds.isEmpty()) {
            return Map.of();
        }
        return depositFlowRepository.findByBookingIdIn(bookingIds).stream()
                .collect(Collectors.groupingBy(DepositFlow::getBookingId,
                        Collectors.collectingAndThen(Collectors.toList(), DepositLedger::of)));
    }
}

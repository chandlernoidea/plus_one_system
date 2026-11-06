package com.nail.demo.solo.service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.nail.demo.solo.common.SoloDepositFlowType;
import com.nail.demo.solo.common.SoloMoneyDirection;
import com.nail.demo.solo.entity.Booking;
import com.nail.demo.solo.entity.BookingItem;
import com.nail.demo.solo.entity.Client;
import com.nail.demo.solo.entity.DepositFlow;
import com.nail.demo.solo.entity.MoneyFlow;
import com.nail.demo.solo.repository.BookingItemRepository;
import com.nail.demo.solo.repository.BookingRepository;
import com.nail.demo.solo.repository.ClientRepository;
import com.nail.demo.solo.repository.DepositFlowRepository;
import com.nail.demo.solo.repository.MoneyFlowRepository;
import com.nail.demo.solo.support.CsvCell;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 三个 CSV 导出：客户、预约、流水。
 *
 * <p>带 BOM、CRLF 换行，Excel 双击打开不乱码；金额不格式化千分位，免得再被当文本。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExportService {

    private static final String BOM = "\uFEFF";
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ClientRepository clientRepository;
    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final MoneyFlowRepository moneyFlowRepository;
    private final DepositFlowRepository depositFlowRepository;

    public String clientsCsv() {
        StringBuilder sb = new StringBuilder(BOM);
        sb.append("短号,昵称,手机后四位,来源,常用色号,备注,首次到访,最近到访,是否删除\r\n");
        for (Client client : clientRepository.search(null)) {
            appendRow(sb,
                    client.getCode(),
                    client.getNickname(),
                    client.getPhoneTail(),
                    client.getSource(),
                    client.getColorNotes(),
                    client.getRemark(),
                    client.getFirstVisitDate() == null ? "" : client.getFirstVisitDate().toString(),
                    client.getLastVisitDate() == null ? "" : client.getLastVisitDate().toString(),
                    Integer.valueOf(1).equals(client.getDeleted()) ? "已删除" : "");
        }
        return sb.toString();
    }

    public String bookingsCsv() {
        StringBuilder sb = new StringBuilder(BOM);
        sb.append("日期,开始,结束,客户短号,客户昵称,状态,时长(分),缓冲(分),项目,金额,来源\r\n");

        List<Booking> bookings = bookingRepository.findAll();
        Map<Long, List<BookingItem>> items = bookings.isEmpty() ? Map.of()
                : bookingItemRepository.findByBookingIdInOrderBySeqAsc(
                        bookings.stream().map(Booking::getId).toList()).stream()
                .collect(Collectors.groupingBy(BookingItem::getBookingId));
        Map<Long, Client> clients = clientRepository.findAllById(
                        bookings.stream().map(Booking::getClientId).distinct().toList())
                .stream().collect(Collectors.toMap(Client::getId, Function.identity()));

        bookings.stream()
                .sorted((a, b) -> b.getStartAt().compareTo(a.getStartAt()))
                .forEach(booking -> {
                    Client client = clients.get(booking.getClientId());
                    List<BookingItem> lines = items.getOrDefault(booking.getId(), List.of());
                    BigDecimal total = lines.stream().map(BookingItem::getPrice)
                            .filter(java.util.Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    appendRow(sb,
                            booking.getStartAt().toLocalDate().toString(),
                            booking.getStartAt().format(DATETIME),
                            booking.getEndAt().format(DATETIME),
                            client == null ? "" : client.getCode(),
                            client == null ? "客户已删除" : client.getNickname(),
                            booking.getStatus(),
                            String.valueOf(booking.getPlannedMinutes()),
                            String.valueOf(booking.getBufferMinutes()),
                            lines.stream().map(BookingItem::getStyleName).collect(Collectors.joining(" + ")),
                            total.toPlainString(),
                            booking.getSource());
                });
        return sb.toString();
    }

    public String flowsCsv() {
        StringBuilder sb = new StringBuilder(BOM);
        sb.append("时间,账本,方向,类别,金额,客户,备注\r\n");

        List<Booking> bookings = bookingRepository.findAll();
        Map<Long, Booking> bookingById = bookings.stream()
                .collect(Collectors.toMap(Booking::getId, Function.identity()));
        Map<Long, Client> clients = clientRepository.findAllById(
                        bookings.stream().map(Booking::getClientId).distinct().toList())
                .stream().collect(Collectors.toMap(Client::getId, Function.identity()));

        moneyFlowRepository.findAll().stream()
                .sorted((a, b) -> b.getHappenedAt().compareTo(a.getHappenedAt()))
                .forEach(flow -> {
                    Client client = clientOf(flow.getBookingId(), bookingById, clients);
                    appendRow(sb,
                            flow.getHappenedAt().format(DATETIME),
                            "收支",
                            SoloMoneyDirection.IN.equals(flow.getDirection()) ? "收入" : "支出",
                            flow.getCategory(),
                            flow.getAmount().toPlainString(),
                            client == null ? "" : client.getCode() + " " + client.getNickname(),
                            flow.getRemark());
                });

        depositFlowRepository.findAll().stream()
                .sorted((a, b) -> b.getHappenedAt().compareTo(a.getHappenedAt()))
                .forEach(flow -> {
                    Client client = clientOf(flow.getBookingId(), bookingById, clients);
                    appendRow(sb,
                            flow.getHappenedAt().format(DATETIME),
                            "定金",
                            soloDepositDirection(flow.getType()),
                            flow.getType(),
                            flow.getAmount().toPlainString(),
                            client == null ? "" : client.getCode() + " " + client.getNickname(),
                            flow.getReason());
                });
        return sb.toString();
    }

    private Client clientOf(Long bookingId, Map<Long, Booking> bookings, Map<Long, Client> clients) {
        if (bookingId == null) {
            return null;
        }
        Booking booking = bookings.get(bookingId);
        return booking == null ? null : clients.get(booking.getClientId());
    }

    private String soloDepositDirection(String type) {
        if (SoloDepositFlowType.COLLECT.equals(type) || SoloDepositFlowType.OFFSET.equals(type)) {
            return "收入";
        }
        return "退出";
    }

    /** 一行 CSV：转义交给 {@link CsvCell}（含公式注入防护）。 */
    private void appendRow(StringBuilder sb, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(CsvCell.escape(values[i]));
        }
        sb.append("\r\n");
    }
}

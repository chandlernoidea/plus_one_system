package com.nail.demo.solo.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nail.demo.exception.BusinessException;
import com.nail.demo.solo.common.SoloBookingStatus;
import com.nail.demo.solo.entity.Booking;
import com.nail.demo.solo.entity.BookingEvent;
import com.nail.demo.solo.entity.Client;
import com.nail.demo.solo.exception.SoloConflictException;
import com.nail.demo.solo.repository.BookingEventRepository;
import com.nail.demo.solo.repository.BookingRepository;
import com.nail.demo.solo.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预约的公共守卫：取单、冲突判定、状态留痕。
 *
 * <p>BookingService 与 DepositService 共用，避免两边各写一份冲突规则。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingGuard {

    /** 占用时段的状态：已定档、到店、服务中。到店后必然在店里占着时间。 */
    public static final List<String> OCCUPY_STATUSES = List.of(
            SoloBookingStatus.BOOKED,
            SoloBookingStatus.ARRIVED,
            SoloBookingStatus.IN_SERVICE);

    /** 单人版只有老板一个操作者。 */
    public static final String OPERATOR = "老板";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final BookingRepository bookingRepository;
    private final BookingEventRepository bookingEventRepository;
    private final ClientRepository clientRepository;

    public Booking requireBooking(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "预约不存在"));
    }

    /**
     * 冲突校验：同一时段只允许一条已定档 / 到店 / 服务中的单，冲突抛 409 并带冲突单摘要。
     */
    public void assertNoConflict(LocalDateTime start, LocalDateTime end, Long excludeBookingId) {
        List<Booking> conflicts = bookingRepository.findOverlapping(OCCUPY_STATUSES, start, end).stream()
                .filter(booking -> !booking.getId().equals(excludeBookingId))
                .toList();
        if (conflicts.isEmpty()) {
            return;
        }

        List<ConflictView> views = toConflictViews(conflicts);
        ConflictView first = views.get(0);
        String message = conflicts.size() == 1
                ? "该时段已被「%s %s」的预约占用（%s–%s）".formatted(
                        first.clientNickname(), first.clientCode(),
                        first.startAt().format(TIME_FORMAT), first.endAt().format(TIME_FORMAT))
                : "该时段已有 %d 单占用，其中「%s %s」（%s–%s）".formatted(
                        conflicts.size(), first.clientNickname(), first.clientCode(),
                        first.startAt().format(TIME_FORMAT), first.endAt().format(TIME_FORMAT));
        throw new SoloConflictException(message, views);
    }

    public void recordEvent(Long bookingId, String action, String fromStatus, String toStatus, String remark) {
        BookingEvent event = new BookingEvent();
        event.setBookingId(bookingId);
        event.setAction(action);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setOperator(OPERATOR);
        event.setRemark(remark);
        bookingEventRepository.save(event);
    }

    private List<ConflictView> toConflictViews(List<Booking> bookings) {
        List<Long> clientIds = bookings.stream().map(Booking::getClientId).distinct().toList();
        Map<Long, Client> clients = clientRepository.findAllById(clientIds).stream()
                .collect(Collectors.toMap(Client::getId, Function.identity()));
        return bookings.stream().map(booking -> {
            Client client = clients.get(booking.getClientId());
            return new ConflictView(
                    booking.getId(),
                    client == null ? null : client.getCode(),
                    client == null ? "客户已删除" : client.getNickname(),
                    booking.getStartAt(),
                    booking.getEndAt(),
                    booking.getStatus());
        }).toList();
    }

    /** 冲突单摘要，随 409 一起返回，供前端标红或不覆盖。 */
    public record ConflictView(Long bookingId,
                               String clientCode,
                               String clientNickname,
                               @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startAt,
                               @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endAt,
                               String status) {
    }
}

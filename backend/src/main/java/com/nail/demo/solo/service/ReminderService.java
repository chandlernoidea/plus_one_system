package com.nail.demo.solo.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.nail.demo.solo.common.SoloBookingStatus;
import com.nail.demo.solo.dto.BookingResponse;
import com.nail.demo.solo.dto.ReminderBoardResponse;
import com.nail.demo.solo.entity.Booking;
import com.nail.demo.solo.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 每天三组要发微信的人：昨日未确认、今日、明日。
 *
 * <p>只提醒老板自己——系统不发消息，只把话说好让人复制。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReminderService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final SoloSettingService settingService;

    public ReminderBoardResponse board(LocalDate date) {
        LocalDate base = date == null ? LocalDate.now() : date;
        LocalDateTime from = base.minusDays(1).atStartOfDay();
        LocalDateTime to = base.plusDays(2).atStartOfDay();

        List<Booking> bookings = bookingRepository.findByStartAtBetweenOrderByStartAtAsc(from, to);
        Map<Long, BookingResponse> views = bookingService.toResponses(bookings).stream()
                .collect(Collectors.toMap(BookingResponse::getId, Function.identity()));

        List<ReminderBoardResponse.Reminder> yesterday = new ArrayList<>();
        List<ReminderBoardResponse.Reminder> today = new ArrayList<>();
        List<ReminderBoardResponse.Reminder> tomorrow = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Booking booking : bookings) {
            LocalDate day = booking.getStartAt().toLocalDate();
            BookingResponse view = views.get(booking.getId());
            if (view == null) {
                continue;
            }
            if (day.equals(base.minusDays(1))) {
                // 昨天没结账的单：没到店、没开始、没取消，都值得问一句
                if (isOpen(booking)) {
                    yesterday.add(build(view, "YESTERDAY", yesterdayScript(view)));
                }
            } else if (day.equals(base)) {
                if (isPendingArrival(booking)) {
                    String script = booking.getStartAt().isBefore(now)
                            ? missedTodayScript(view)
                            : todayScript(view, booking);
                    today.add(build(view, "TODAY", script));
                }
            } else if (day.equals(base.plusDays(1))) {
                if (isPendingArrival(booking)) {
                    tomorrow.add(build(view, "TOMORROW", tomorrowScript(view, booking)));
                }
            }
        }

        return ReminderBoardResponse.builder().yesterday(yesterday).today(today).tomorrow(tomorrow).build();
    }

    private boolean isOpen(Booking booking) {
        String status = booking.getStatus();
        return SoloBookingStatus.BOOKED.equals(status)
                || SoloBookingStatus.ARRIVED.equals(status)
                || SoloBookingStatus.IN_SERVICE.equals(status)
                || SoloBookingStatus.WAIT_DEPOSIT.equals(status);
    }

    private boolean isPendingArrival(Booking booking) {
        return SoloBookingStatus.BOOKED.equals(booking.getStatus())
                || SoloBookingStatus.WAIT_DEPOSIT.equals(booking.getStatus());
    }

    private ReminderBoardResponse.Reminder build(BookingResponse view, String group, String script) {
        StringBuilder styles = new StringBuilder();
        view.getItems().forEach(item -> {
            if (styles.length() > 0) {
                styles.append(" + ");
            }
            styles.append(item.getStyleName());
        });
        return ReminderBoardResponse.Reminder.builder()
                .bookingId(view.getId())
                .clientCode(view.getClientCode())
                .clientNickname(view.getClientNickname())
                .startAt(view.getStartAt())
                .status(view.getStatus())
                .styles(styles.toString())
                .script(script)
                .build();
    }

    private String depositText() {
        BigDecimal amount = settingService.depositAmount();
        return amount.stripTrailingZeros().toPlainString();
    }

    private String yesterdayScript(BookingResponse view) {
        return "Hi %s，昨天 %s 的档期没等到你，还好吗？想改天的话我帮你重新排～"
                .formatted(view.getClientNickname(), view.getStartAt().format(TIME));
    }

    private String missedTodayScript(BookingResponse view) {
        return "Hi %s，今天 %s 的档期还留着，现在过来还来得及，来不了咱们改天～"
                .formatted(view.getClientNickname(), view.getStartAt().format(TIME));
    }

    private String todayScript(BookingResponse view, Booking booking) {
        String time = booking.getStartAt().format(TIME);
        if (SoloBookingStatus.WAIT_DEPOSIT.equals(booking.getStatus())) {
            return "Hi %s，今天 %s 的档期先给你留着，定金 %s 微信转我就锁档～"
                    .formatted(view.getClientNickname(), time, depositText());
        }
        return "Hi %s，提醒一下今天 %s 到店做%s，档期给你留着，有变化提前说一声～"
                .formatted(view.getClientNickname(), time, stylesText(view));
    }

    private String tomorrowScript(BookingResponse view, Booking booking) {
        String time = booking.getStartAt().format(TIME);
        if (SoloBookingStatus.WAIT_DEPOSIT.equals(booking.getStatus())) {
            return "Hi %s，明天 %s 的档期先给你留着，定金 %s 微信转我就锁档～"
                    .formatted(view.getClientNickname(), time, depositText());
        }
        return "Hi %s，明天 %s 到店做%s，别忘了～"
                .formatted(view.getClientNickname(), time, stylesText(view));
    }

    private String stylesText(BookingResponse view) {
        String text = view.getItems().stream()
                .map(item -> item.getStyleName())
                .collect(Collectors.joining(" + "));
        return text.isEmpty() ? "款" : text;
    }
}

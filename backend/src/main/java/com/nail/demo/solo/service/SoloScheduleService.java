package com.nail.demo.solo.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.nail.demo.exception.BusinessException;
import com.nail.demo.solo.common.SoloBookingStatus;
import com.nail.demo.solo.common.SoloPurchaseStatus;
import com.nail.demo.solo.dto.BookingResponse;
import com.nail.demo.solo.dto.CalendarRangeResponse;
import com.nail.demo.solo.dto.DayScheduleResponse;
import com.nail.demo.solo.dto.WeekScheduleResponse;
import com.nail.demo.solo.entity.Booking;
import com.nail.demo.solo.entity.SlotBlock;
import com.nail.demo.solo.repository.BookingRepository;
import com.nail.demo.solo.repository.MaterialItemRepository;
import com.nail.demo.solo.repository.SlotBlockRepository;
import com.nail.demo.solo.support.SoloScheduleRules;
import com.nail.demo.solo.support.TimeWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 排期：日作业单 / 周视图。空档与「可塞 N 分钟」都由 {@link SoloScheduleRules} 算出。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SoloScheduleService {

    private static final int WEEK_DAYS = 7;

    private final SlotBlockRepository slotBlockRepository;
    private final BookingRepository bookingRepository;
    private final SlotAvailabilityService slotAvailability;
    private final BookingService bookingService;
    private final MaterialItemRepository materialItemRepository;

    public DayScheduleResponse day(LocalDate date) {
        List<TimeWindow> openWindows = slotAvailability.openWindows(date);
        List<TimeWindow> gaps = SoloScheduleRules.freeGaps(
                openWindows, slotAvailability.busyWindows(date, null));

        List<Booking> bookings = slotAvailability.bookingsOfDay(date);
        List<BookingResponse> views = bookingService.toResponses(bookings);
        List<SlotBlock> blocks = slotBlockRepository.findByWorkDateOrderByStartTimeAsc(date);

        List<BookingResponse> active = views.stream()
                .filter(view -> !SoloBookingStatus.CANCELLED.equals(view.getStatus()))
                .toList();

        DayScheduleResponse.Stats stats = DayScheduleResponse.Stats.builder()
                .count(active.size())
                .plannedMinutes(active.stream()
                        .mapToInt(view -> view.getPlannedMinutes() + view.getBufferMinutes())
                        .sum())
                .depositCollected(active.stream()
                        .map(BookingResponse::getDepositCollected)
                        .filter(amount -> amount != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .gapMinutes(gaps.stream().mapToLong(TimeWindow::minutes).sum())
                .purchasePending((int) materialItemRepository.countByPurchaseStatusIn(
                        List.of(SoloPurchaseStatus.TODO,
                                SoloPurchaseStatus.ORDERED,
                                SoloPurchaseStatus.SHIPPING)))
                .build();

        return DayScheduleResponse.builder()
                .date(date)
                .openWindows(openWindows.stream().map(this::toWindow).toList())
                .blocks(blocks.stream().map(this::toBlockView).toList())
                .bookings(views)
                .gaps(gaps.stream().map(this::toWindow).toList())
                .stats(stats)
                .build();
    }

    public WeekScheduleResponse week(LocalDate from) {
        LocalDate start = from == null ? LocalDate.now() : from;
        return WeekScheduleResponse.builder()
                .from(start)
                .to(start.plusDays(WEEK_DAYS - 1L))
                .days(java.util.stream.IntStream.range(0, WEEK_DAYS)
                        .mapToObj(offset -> day(start.plusDays(offset)))
                        .toList())
                .build();
    }

    /**
     * 日历视图：给一段日期，按天分组返回预约（含已取消/爽约，前端画淡）。
     * 最多一次 62 天（两个月），够画月视图的 6 行格子。
     */
    public CalendarRangeResponse calendar(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now() : from;
        LocalDate end = to == null ? start.plusDays(WEEK_DAYS - 1L) : to;
        if (end.isBefore(start)) {
            throw new BusinessException("结束日期不能早于开始日期");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) > 62) {
            throw new BusinessException("一次最多查 62 天");
        }

        List<Booking> bookings = bookingRepository.findByStartAtBetweenOrderByStartAtAsc(
                start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        // 复用预约返回体，前端拿到的字段和作业单一致（客户、项目、定金都在）
        java.util.Map<LocalDate, List<BookingResponse>> byDate = bookingService.toResponses(bookings).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        view -> view.getStartAt().toLocalDate()));

        List<CalendarRangeResponse.DayBookings> days = java.util.stream.IntStream
                .range(0, (int) java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1)
                .mapToObj(offset -> {
                    LocalDate date = start.plusDays(offset);
                    return CalendarRangeResponse.DayBookings.builder()
                            .date(date)
                            .bookings(byDate.getOrDefault(date, List.of()))
                            .build();
                })
                .toList();

        return CalendarRangeResponse.builder().from(start).to(end).days(days).build();
    }

    private DayScheduleResponse.Window toWindow(TimeWindow window) {
        return DayScheduleResponse.Window.builder()
                .start(window.start())
                .end(window.end())
                .minutes(window.minutes())
                .build();
    }

    private DayScheduleResponse.SlotBlockView toBlockView(SlotBlock block) {
        return DayScheduleResponse.SlotBlockView.builder()
                .id(block.getId())
                .startTime(block.getStartTime())
                .endTime(block.getEndTime())
                .type(block.getType())
                .remark(block.getRemark())
                .build();
    }
}

package com.nail.demo.solo.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.nail.demo.solo.common.SoloSlotType;
import com.nail.demo.solo.entity.Booking;
import com.nail.demo.solo.entity.SlotBlock;
import com.nail.demo.solo.repository.BookingRepository;
import com.nail.demo.solo.repository.SlotBlockRepository;
import com.nail.demo.solo.support.SoloScheduleRules;
import com.nail.demo.solo.support.TimeWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 可排期计算：营业窗口、占用窗口、空档。排期页与插队校验共用。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SlotAvailabilityService {

    private final SlotBlockRepository slotBlockRepository;
    private final BookingRepository bookingRepository;

    /** 当天可营业窗口：营业 + 机动位合并；没有配置时用 8:30–22:30 兜底。 */
    public List<TimeWindow> openWindows(LocalDate date) {
        List<TimeWindow> windows = slotBlockRepository.findByWorkDateOrderByStartTimeAsc(date).stream()
                .filter(block -> SoloSlotType.bookable(block.getType()))
                .map(block -> new TimeWindow(date.atTime(block.getStartTime()), date.atTime(block.getEndTime())))
                .filter(window -> window.minutes() > 0)
                .sorted(Comparator.comparing(TimeWindow::start))
                .toList();
        return windows.isEmpty() ? List.of(SoloScheduleRules.defaultDayWindow(date)) : merge(windows);
    }

    /** 当天已占用窗口，可按 id 排除某单（改期 / 顺延时排除自己）。 */
    public List<TimeWindow> busyWindows(LocalDate date, Long excludeBookingId) {
        return bookingRepository
                .findByStatusInAndStartAtBetweenOrderByStartAtAsc(
                        BookingGuard.OCCUPY_STATUSES,
                        date.atStartOfDay(),
                        date.plusDays(1).atStartOfDay())
                .stream()
                .filter(booking -> excludeBookingId == null || !booking.getId().equals(excludeBookingId))
                .map(booking -> new TimeWindow(booking.getStartAt(), booking.getEndAt()))
                .toList();
    }

    public List<TimeWindow> freeGaps(LocalDate date, Long excludeBookingId) {
        return SoloScheduleRules.freeGaps(openWindows(date), busyWindows(date, excludeBookingId));
    }

    /**
     * 找到包含 start 的空档；用于插队与「可塞 N 分钟」。
     */
    public Optional<TimeWindow> gapContaining(LocalDate date, LocalDateTime start, Long excludeBookingId) {
        return SoloScheduleRules.gapContaining(freeGaps(date, excludeBookingId), start);
    }

    /** 当天所有预约（含非占用状态），排期页展示用。 */
    public List<Booking> bookingsOfDay(LocalDate date) {
        return bookingRepository.findByStartAtBetweenOrderByStartAtAsc(
                date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    /** 合并首尾相接 / 重叠的窗口。 */
    private List<TimeWindow> merge(List<TimeWindow> windows) {
        List<TimeWindow> merged = new ArrayList<>();
        for (TimeWindow window : windows) {
            if (merged.isEmpty()) {
                merged.add(window);
                continue;
            }
            TimeWindow last = merged.get(merged.size() - 1);
            if (!window.start().isAfter(last.end())) {
                merged.set(merged.size() - 1, new TimeWindow(last.start(),
                        window.end().isAfter(last.end()) ? window.end() : last.end()));
            } else {
                merged.add(window);
            }
        }
        return merged;
    }
}

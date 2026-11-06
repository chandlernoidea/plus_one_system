package com.nail.demo.solo.support;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 单人版排期规则的纯函数集合：不依赖 Spring / 数据库，便于单测。
 *
 * <p>这些规则是 P2 的业务核心，Service 只负责取数、落库与留痕。
 */
public final class SoloScheduleRules {

    /** 空档小于该分钟数不展示为「可塞」。 */
    public static final int MIN_USABLE_GAP_MINUTES = 15;

    private SoloScheduleRules() {
    }

    /** 占用结束时间 = 开始 + 各项目时长之和 + 缓冲。 */
    public static LocalDateTime endOf(LocalDateTime start, int plannedMinutes, int bufferMinutes) {
        return start.plusMinutes((long) plannedMinutes + bufferMinutes);
    }

    /** 距开始还有多少小时（向下取整，已过去返回负数）。 */
    public static long hoursBefore(LocalDateTime now, LocalDateTime start) {
        return Duration.between(now, start).toHours();
    }

    /**
     * 改期是否免费：距开始 ≥ 阈值小时数且从未用过免费改期。
     * 阈值来自设置页（默认 48 小时）。
     */
    public static boolean isFreeReschedule(long hoursBefore, boolean freeRescheduleUsed, int thresholdHours) {
        return hoursBefore >= thresholdHours && !freeRescheduleUsed;
    }

    /** 冲突判定：已占用时段与目标时段重叠即冲突（首尾相接不算）。 */
    public static Optional<TimeWindow> firstOverlap(List<TimeWindow> busy, TimeWindow target) {
        return busy.stream().filter(target::overlaps).findFirst();
    }

    public static boolean hasOverlap(List<TimeWindow> busy, TimeWindow target) {
        return firstOverlap(busy, target).isPresent();
    }

    /**
     * 空档 = 可营业时段 − 已占用时段，结果按时间升序、且只保留 ≥{@link #MIN_USABLE_GAP_MINUTES} 的段。
     */
    public static List<TimeWindow> freeGaps(List<TimeWindow> openWindows, List<TimeWindow> busy) {
        List<TimeWindow> gaps = new ArrayList<>();
        List<TimeWindow> sortedBusy = busy.stream()
                .sorted(Comparator.comparing(TimeWindow::start))
                .toList();

        for (TimeWindow open : openWindows) {
            LocalDateTime cursor = open.start();
            for (TimeWindow occupied : sortedBusy) {
                if (!occupied.end().isAfter(cursor) || !occupied.start().isBefore(open.end())) {
                    continue;
                }
                if (occupied.start().isAfter(cursor)) {
                    gaps.add(new TimeWindow(cursor, occupied.start()));
                }
                if (occupied.end().isAfter(cursor)) {
                    cursor = occupied.end();
                }
                if (!cursor.isBefore(open.end())) {
                    break;
                }
            }
            if (cursor.isBefore(open.end())) {
                gaps.add(new TimeWindow(cursor, open.end()));
            }
        }
        return gaps.stream()
                .filter(gap -> gap.minutes() >= MIN_USABLE_GAP_MINUTES)
                .toList();
    }

    /** 找到包含指定开始时刻的空档；开始时刻必须严格落在空档内。 */
    public static Optional<TimeWindow> gapContaining(List<TimeWindow> gaps, LocalDateTime start) {
        return gaps.stream()
                .filter(gap -> !start.isBefore(gap.start()) && start.isBefore(gap.end()))
                .findFirst();
    }

    /**
     * 插队校验：新单占用（时长 + 缓冲）必须落在空档内，超出即拒绝。
     */
    public static boolean fits(TimeWindow gap, LocalDateTime start, int plannedMinutes, int bufferMinutes) {
        LocalDateTime end = endOf(start, plannedMinutes, bufferMinutes);
        return !start.isBefore(gap.start()) && !end.isAfter(gap.end());
    }


}

package com.nail.demo.solo.support;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 半开区间时间窗 [start, end)，排期、冲突与空档计算统一用它。
 */
public record TimeWindow(LocalDateTime start, LocalDateTime end) {

    public TimeWindow {
        if (start == null || end == null) {
            throw new IllegalArgumentException("时间窗起止不能为空");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("时间窗结束必须晚于开始");
        }
    }

    public long minutes() {
        return Duration.between(start, end).toMinutes();
    }

    /** 半开区间重叠：首尾相接不算冲突。 */
    public boolean overlaps(TimeWindow other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}

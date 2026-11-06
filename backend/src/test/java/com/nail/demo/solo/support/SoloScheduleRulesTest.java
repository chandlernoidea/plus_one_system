package com.nail.demo.solo.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P2 三条核心规则的单元测试：占用/改期/冲突/插队/空档。
 */
class SoloScheduleRulesTest {

    private static LocalDateTime at(int hour, int minute) {
        return LocalDate.of(2026, 11, 7).atTime(hour, minute);
    }

    @Test
    @DisplayName("占用 = 各项目时长之和 + 缓冲（90 + 20 → 11:50）")
    void occupancyAddsBuffer() {
        assertEquals(at(11, 50), SoloScheduleRules.endOf(at(10, 0), 90, 20));
    }

    @Test
    @DisplayName("改期：距开始 ≥48 小时且未用过免费改期才免费")
    void rescheduleIsFreeOnlyWhenQualified() {
        assertTrue(SoloScheduleRules.isFreeReschedule(60, false, 48));
        assertTrue(SoloScheduleRules.isFreeReschedule(48, false, 48), "48 小时整算满足阈值");
        assertFalse(SoloScheduleRules.isFreeReschedule(47, false, 48), "不足 48 小时需扣定金");
        assertFalse(SoloScheduleRules.isFreeReschedule(60, true, 48), "免费额度已用过");
        assertTrue(SoloScheduleRules.isFreeReschedule(30, false, 24), "阈值改成 24 小时后 30 小时算免费");
    }

    @Test
    @DisplayName("冲突：时段重叠才算冲突，首尾相接不算")
    void conflictOnlyWhenOverlapping() {
        TimeWindow existing = new TimeWindow(at(10, 0), at(11, 50));

        assertTrue(SoloScheduleRules.hasOverlap(List.of(existing),
                new TimeWindow(at(11, 0), at(12, 0))), "交叉重叠应冲突");
        assertTrue(SoloScheduleRules.hasOverlap(List.of(existing),
                new TimeWindow(at(9, 0), at(12, 0))), "包含关系应冲突");
        assertFalse(SoloScheduleRules.hasOverlap(List.of(existing),
                new TimeWindow(at(11, 50), at(13, 0))), "首尾相接不算冲突");
        assertFalse(SoloScheduleRules.hasOverlap(List.of(existing),
                new TimeWindow(at(8, 0), at(10, 0))), "紧邻在前不算冲突");
    }

    @Test
    @DisplayName("插队：新单时长 + 缓冲必须落在空档内，超出拒绝")
    void jumpRequiresGapFits() {
        TimeWindow gap = new TimeWindow(at(13, 0), at(14, 30));

        assertTrue(SoloScheduleRules.fits(gap, at(13, 0), 60, 20), "80 分钟塞得进 90 分钟空档");
        assertTrue(SoloScheduleRules.fits(gap, at(13, 0), 70, 20), "刚好 90 分钟仍可塞");
        assertFalse(SoloScheduleRules.fits(gap, at(13, 0), 90, 20), "110 分钟超出 90 分钟空档，应拒绝");
        assertFalse(SoloScheduleRules.fits(gap, at(13, 30), 60, 20), "起点后剩 60 分钟，80 分钟塞不下");
    }

    @Test
    @DisplayName("空档：营业段减去已占用，只保留 ≥15 分钟的段")
    void freeGaps() {
        List<TimeWindow> open = List.of(new TimeWindow(at(8, 30), at(22, 30)));
        List<TimeWindow> busy = List.of(new TimeWindow(at(10, 0), at(11, 50)));

        List<TimeWindow> gaps = SoloScheduleRules.freeGaps(open, busy);

        assertEquals(2, gaps.size());
        assertEquals(at(8, 30), gaps.get(0).start());
        assertEquals(at(10, 0), gaps.get(0).end());
        assertEquals(at(11, 50), gaps.get(1).start());
        assertEquals(at(22, 30), gaps.get(1).end());
    }

    @Test
    @DisplayName("空档定位：开始时刻必须落在空档内，贴着结束点不算")
    void gapContaining() {
        List<TimeWindow> gaps = List.of(new TimeWindow(at(13, 0), at(14, 30)));

        assertTrue(SoloScheduleRules.gapContaining(gaps, at(13, 30)).isPresent());
        assertTrue(SoloScheduleRules.gapContaining(gaps, at(14, 30)).isEmpty());
        assertTrue(SoloScheduleRules.gapContaining(gaps, at(14, 0)).isPresent());
    }
}

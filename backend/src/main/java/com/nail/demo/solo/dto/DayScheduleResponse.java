package com.nail.demo.solo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一天作业单：营业窗口 + 预约块 + 空档 + 顶部统计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DayScheduleResponse {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** 可营业时间窗（营业 + 机动位合并后的结果）。 */
    private List<Window> openWindows;

    /** 当天的营业安排原始分段（含休息 / 外出）。 */
    private List<SlotBlockView> blocks;

    /** 占用档期的预约（含待定金、已取消等，前端按状态渲染）。 */
    private List<BookingResponse> bookings;

    /** 空档：只保留 ≥15 分钟的段。 */
    private List<Window> gaps;

    private Stats stats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Window {

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime start;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime end;

        /** 该段可用分钟数，前端显示「可塞 N 分钟」。 */
        private long minutes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlotBlockView {

        private Long id;

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime startTime;

        @JsonFormat(pattern = "HH:mm:ss")
        private LocalTime endTime;

        private String type;
        private String remark;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {

        /** 当天未取消的单数。 */
        private int count;

        /** 当天占用时长合计（含缓冲）。 */
        private int plannedMinutes;

        /** 当天定金收取合计。 */
        private BigDecimal depositCollected;

        /** 空档总分钟数。 */
        private long gapMinutes;

        /** 还没备好的料件数（待采购 / 已下单 / 在途）。 */
        private int purchasePending;
    }
}

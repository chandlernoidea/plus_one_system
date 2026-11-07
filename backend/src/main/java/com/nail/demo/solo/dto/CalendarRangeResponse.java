package com.nail.demo.solo.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日历视图（月 / 周）用的排班数据：只按日期分组给出预约，不含空档与统计。
 *
 * <p>和 {@link WeekScheduleResponse} 的区别：那边是一天一天的完整作业单（含空档、统计），
 * 只适合展开某一天；这里是一次拿一整段日期，够画格子就行，几十单也不重。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarRangeResponse {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate from;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate to;

    private List<DayBookings> days;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayBookings {

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate date;

        /** 当天的预约（含已取消 / 爽约，前端按状态上色并画淡）。 */
        private List<BookingResponse> bookings;
    }
}

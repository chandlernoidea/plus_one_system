package com.nail.demo.solo.controller;

import java.time.LocalDate;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.solo.dto.DayScheduleResponse;
import com.nail.demo.solo.dto.WeekScheduleResponse;
import com.nail.demo.solo.service.SoloScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 排期接口：日作业单（营业窗口 + 预约 + 空档 + 统计）与周视图。
 */
@RestController
@RequestMapping("/api/solo/schedule")
@RequiredArgsConstructor
public class SoloScheduleController {

    private final SoloScheduleService scheduleService;

    /** 日作业单：营业窗口 + 预约 + 空档 + 统计。 */
    @GetMapping("/day")
    public ApiResponse<DayScheduleResponse> day(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return ApiResponse.success(scheduleService.day(date == null ? LocalDate.now() : date));
    }

    /** 周视图：从 from 起 7 天。 */
    @GetMapping("/week")
    public ApiResponse<WeekScheduleResponse> week(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate from) {
        return ApiResponse.success(scheduleService.week(from == null ? LocalDate.now() : from));
    }
}

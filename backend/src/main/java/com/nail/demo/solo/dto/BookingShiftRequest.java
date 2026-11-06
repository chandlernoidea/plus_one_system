package com.nail.demo.solo.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 顺延入参：日期、起点时刻、平移分钟数。
 */
@Data
public class BookingShiftRequest {

    @NotNull(message = "顺延日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** 从该时刻起（含）的预约整体平移；不传则整天平移。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fromTime;

    @NotNull(message = "顺延分钟数不能为空")
    @Min(value = 15, message = "顺延至少 15 分钟")
    @Max(value = 480, message = "顺延最多 480 分钟")
    private Integer minutes;

    @Size(max = 200, message = "备注最长 200 字")
    private String remark;
}

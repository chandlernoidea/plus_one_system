package com.nail.demo.solo.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 保存设置的入参。与 {@link SettingsResponse} 分开，
 * 是为了让校验注解落在请求体上（响应体不该承担校验职责）。
 */
@Data
public class SettingsRequest {

    @NotNull(message = "开门时间不能为空")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "开门时间格式应为 HH:mm")
    private String openTime;

    @NotNull(message = "打烊时间不能为空")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "打烊时间格式应为 HH:mm")
    private String closeTime;

    @NotNull(message = "默认缓冲不能为空")
    @Min(value = 0, message = "默认缓冲应在 0–120 分钟之间")
    @Max(value = 120, message = "默认缓冲应在 0–120 分钟之间")
    private Integer bufferMinutes;

    @NotNull(message = "定金不能为空")
    @DecimalMin(value = "0", message = "定金应在 0–1000 元之间")
    @DecimalMax(value = "1000", message = "定金应在 0–1000 元之间")
    private BigDecimal depositAmount;

    @NotNull(message = "免费改期阈值不能为空")
    @Min(value = 1, message = "免费改期阈值应在 1–168 小时之间")
    @Max(value = 168, message = "免费改期阈值应在 1–168 小时之间")
    private Integer freeRescheduleHours;
}

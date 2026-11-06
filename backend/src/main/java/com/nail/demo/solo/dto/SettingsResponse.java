package com.nail.demo.solo.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设置：营业时间模板、默认缓冲、定金金额、免费改期阈值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsResponse {

    /** HH:mm */
    private String openTime;

    /** HH:mm */
    private String closeTime;

    /** 建单默认缓冲（分钟）。 */
    private Integer bufferMinutes;

    /** 定金固定金额（元）。 */
    private BigDecimal depositAmount;

    /** 免费改期阈值（距开始多少小时）。 */
    private Integer freeRescheduleHours;
}

package com.nail.demo.solo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 改期结果：是否真的改了、是否免费、是否需要老板先决定定金。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRescheduleResponse {

    /** 是否真的改了期；false 表示停在「需扣定金」这一步等老板选择。 */
    private boolean changed;

    /** 是否走了免费改期。 */
    private boolean free;

    /** 是否需要扣定金（不满足免费条件时为 true）。 */
    private boolean needDepositDeduction;

    /** 距原开始的小时数，供前端展示「提前 60 小时」。 */
    private long hoursBefore;

    private String message;

    private BookingResponse booking;
}

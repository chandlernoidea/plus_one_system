package com.nail.demo.solo.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一单的定金台账：四条流水各自合计，余额 = 收取 − 抵扣 − 退还 − 没收。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositSummaryResponse {

    private Long bookingId;
    private BigDecimal collected;
    private BigDecimal offset;
    private BigDecimal refunded;
    private BigDecimal forfeited;
    private BigDecimal balance;

    /** NONE / PAID / OFFSET / REFUNDED / FORFEITED。 */
    private String state;

    private List<DepositResponse> flows;
}

package com.nail.demo.solo.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 月度概览。income/expense 来自聚合查询，detailIncome/detailExpense 来自明细逐笔累加，
 * 两者比对即为对账结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceSummaryResponse {

    private String month;
    private BigDecimal income;
    private BigDecimal expense;
    private BigDecimal net;

    private BigDecimal depositCollected;
    private BigDecimal depositRefunded;
    private BigDecimal depositForfeited;

    private BigDecimal detailIncome;
    private BigDecimal detailExpense;

    /** 收入 − 支出 与明细求和一致时为 true。 */
    private boolean balanced;
}

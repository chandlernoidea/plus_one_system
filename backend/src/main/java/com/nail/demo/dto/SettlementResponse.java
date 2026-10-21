package com.nail.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementResponse {

    private Long id;
    private Long staffId;
    private String staffName;
    private String settleMonth;
    private BigDecimal baseSalary;
    private Integer completedCount;
    private BigDecimal totalRevenue;
    private BigDecimal commissionAmount;
    private Integer goodReviewCount;
    private BigDecimal goodReviewBonusAmount;
    private Integer badReviewCount;
    private BigDecimal badReviewDeductionAmount;
    private BigDecimal reviewAdjustment;
    private Integer noShowCount;
    private BigDecimal noShowDeduction;
    private BigDecimal finalPayable;
    private BigDecimal finalPayableBeforeFloor;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime settledAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}

package com.nail.demo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

@Data
@NoArgsConstructor
@Entity
@Table(name = "monthly_settlement")
public class MonthlySettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "settle_month", nullable = false, length = 7, columnDefinition = "CHAR(7)")
    private String settleMonth;

    @Column(name = "base_salary", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "completed_count", nullable = false)
    private Integer completedCount;

    @Column(name = "total_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalRevenue;

    @Column(name = "commission_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "good_review_count", nullable = false)
    private Integer goodReviewCount;

    @Column(name = "good_review_bonus_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal goodReviewBonusAmount;

    @Column(name = "bad_review_count", nullable = false)
    private Integer badReviewCount;

    @Column(name = "bad_review_deduction_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal badReviewDeductionAmount;

    @Column(name = "review_adjustment", nullable = false, precision = 10, scale = 2)
    private BigDecimal reviewAdjustment;

    @Column(name = "no_show_count", nullable = false)
    private Integer noShowCount;

    @Column(name = "no_show_deduction", nullable = false, precision = 10, scale = 2)
    private BigDecimal noShowDeduction;

    @Column(name = "final_payable", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalPayable;

    @Column(name = "final_payable_before_floor", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalPayableBeforeFloor;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "settled_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime settledAt;

    @Column(name = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = "DRAFT";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

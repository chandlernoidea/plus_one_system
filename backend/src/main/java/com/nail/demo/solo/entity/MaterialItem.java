package com.nail.demo.solo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.nail.demo.solo.common.SoloPurchaseStatus;
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

/**
 * 物料库存。赞助物料成本记 0，另记赞助商与赞助价值。
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "solo_material_item")
public class MaterialItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "unit", nullable = false, length = 16)
    private String unit = "个";

    @Column(name = "unit_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "sponsored", nullable = false)
    private Integer sponsored = 0;

    @Column(name = "sponsor_name", length = 80)
    private String sponsorName;

    @Column(name = "sponsor_value", precision = 10, scale = 2)
    private BigDecimal sponsorValue;

    /** 采购状态：待采购 → 已下单 → 在途 → 已到货 → 已备好。 */
    @Column(name = "purchase_status", nullable = false, length = 20)
    private String purchaseStatus;

    @Column(name = "remark", length = 255)
    private String remark;

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
        if (unit == null) {
            unit = "个";
        }
        if (unitCost == null) {
            unitCost = BigDecimal.ZERO;
        }
        if (quantity == null) {
            quantity = BigDecimal.ZERO;
        }
        if (sponsored == null) {
            sponsored = 0;
        }
        if (purchaseStatus == null) {
            purchaseStatus = SoloPurchaseStatus.TODO;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

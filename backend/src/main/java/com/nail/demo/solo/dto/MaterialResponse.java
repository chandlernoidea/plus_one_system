package com.nail.demo.solo.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 物料返回体：库存、成本、赞助信息与采购状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialResponse {

    private Long id;
    private String name;
    private String unit;
    private BigDecimal unitCost;
    private BigDecimal quantity;
    private Integer sponsored;
    private String sponsorName;
    private BigDecimal sponsorValue;
    private String purchaseStatus;
    private String remark;
}

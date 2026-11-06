package com.nail.demo.solo.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 「今天要买的料」的一行：某天所有预约的款式材料清单，按名字聚合后和库存对一遍。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialNeedResponse {

    private String name;

    /** 当天所有预约一共要用多少。 */
    private BigDecimal needQty;

    /** 命中库存时的物料 ID（没建过这个物料则为空）。 */
    private Long materialId;

    private String unit;

    /** 当前库存。 */
    private BigDecimal stockQty;

    /** 缺口 = max(0, 需要 − 库存)。 */
    private BigDecimal shortfall;

    /** 库存里的采购状态；没建过物料时为 null。 */
    private String purchaseStatus;

    private Integer sponsored;

    /** 够不够：库存 ≥ 需要 且 采购状态已备好。 */
    private boolean ready;
}

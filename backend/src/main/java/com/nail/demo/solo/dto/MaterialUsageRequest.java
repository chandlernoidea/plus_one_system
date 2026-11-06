package com.nail.demo.solo.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 按单领料入参：物料与数量。
 */
@Data
public class MaterialUsageRequest {

    @NotNull(message = "物料不能为空")
    private Long materialId;

    @NotNull(message = "领用数量不能为空")
    @DecimalMin(value = "0.01", message = "领用数量必须大于 0")
    private BigDecimal quantity;

    @Size(max = 255, message = "备注最长 255 字")
    private String remark;
}

package com.nail.demo.solo.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建 / 编辑物料入参；赞助物料成本会被强制归零。
 */
@Data
public class MaterialSaveRequest {

    @NotBlank(message = "物料名称不能为空")
    @Size(max = 80, message = "物料名称最长 80 字")
    private String name;

    @Size(max = 16, message = "单位最长 16 字")
    private String unit;

    @DecimalMin(value = "0", message = "成本不能为负")
    private BigDecimal unitCost;

    @DecimalMin(value = "0", message = "数量不能为负")
    private BigDecimal quantity;

    /** 是否赞助：赞助物料成本记 0，另记赞助商与价值。 */
    private Integer sponsored;

    @Size(max = 80, message = "赞助商最长 80 字")
    private String sponsorName;

    private BigDecimal sponsorValue;

    /** TODO / ORDERED / SHIPPING / ARRIVED / READY。 */
    private String purchaseStatus;

    @Size(max = 255, message = "备注最长 255 字")
    private String remark;
}

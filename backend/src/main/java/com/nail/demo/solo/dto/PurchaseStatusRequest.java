package com.nail.demo.solo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改采购状态的入参。
 */
@Data
public class PurchaseStatusRequest {

    /** TODO / ORDERED / SHIPPING / ARRIVED / READY。 */
    @NotBlank(message = "采购状态不能为空")
    private String status;
}

package com.nail.demo.solo.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DepositRequest {

    /** COLLECT 收取 / OFFSET 抵扣 / REFUND 退还 / FORFEIT 没收。 */
    @NotBlank(message = "定金流水类型不能为空")
    private String type;

    /** 金额，不传按定金标准额 30 元（抵扣时按当前可退余额）。 */
    private BigDecimal amount;

    @Size(max = 200, message = "原因最长 200 字")
    private String reason;
}

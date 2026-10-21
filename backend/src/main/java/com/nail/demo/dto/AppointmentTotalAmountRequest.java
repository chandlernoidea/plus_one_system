package com.nail.demo.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AppointmentTotalAmountRequest {

    @NotNull(message = "订单金额不能为空")
    @DecimalMin(value = "0", message = "订单金额不能小于0")
    @Digits(integer = 8, fraction = 2, message = "订单金额最多保留2位小数")
    private BigDecimal totalAmount;
}

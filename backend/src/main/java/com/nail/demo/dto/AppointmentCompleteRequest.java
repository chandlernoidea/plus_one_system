package com.nail.demo.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AppointmentCompleteRequest {

    @Size(max = 6, message = "完工图最多上传6张")
    private List<String> finishImageUrls;

    @NotNull(message = "订单金额不能为空")
    @DecimalMin(value = "0", message = "订单金额不能小于0")
    @Digits(integer = 8, fraction = 2, message = "订单金额最多保留2位小数")
    private BigDecimal totalAmount;
}

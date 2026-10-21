package com.nail.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class CompensationRuleRequest {

    @NotNull(message = "美甲师不能为空")
    private Long staffId;

    @NotNull(message = "底薪不能为空")
    @DecimalMin(value = "0", message = "底薪不能小于0")
    @Digits(integer = 8, fraction = 2, message = "底薪最多保留2位小数")
    private BigDecimal baseSalary;

    @NotNull(message = "提成比例不能为空")
    @DecimalMin(value = "0", message = "提成比例不能小于0")
    @DecimalMax(value = "1", message = "提成比例不能大于1")
    @Digits(integer = 1, fraction = 4, message = "提成比例最多保留4位小数")
    private BigDecimal commissionRate;

    @NotNull(message = "好评奖励不能为空")
    @DecimalMin(value = "0", message = "好评奖励不能小于0")
    @Digits(integer = 8, fraction = 2, message = "好评奖励最多保留2位小数")
    private BigDecimal goodReviewBonus;

    @NotNull(message = "差评扣减不能为空")
    @DecimalMin(value = "0", message = "差评扣减不能小于0")
    @Digits(integer = 8, fraction = 2, message = "差评扣减最多保留2位小数")
    private BigDecimal badReviewDeduction;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate effectiveFrom;
}

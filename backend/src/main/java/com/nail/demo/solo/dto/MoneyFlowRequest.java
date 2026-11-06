package com.nail.demo.solo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 记一笔收支的入参：方向、类别、金额、发生时间与备注。
 */
@Data
public class MoneyFlowRequest {

    /** IN 收入 / OUT 支出。 */
    @NotBlank(message = "收支方向不能为空")
    private String direction;

    /** SERVICE 服务收入 / MATERIAL 物料支出 / OTHER 其他。 */
    @NotBlank(message = "收支类别不能为空")
    private String category;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于 0")
    private BigDecimal amount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime happenedAt;

    private Long bookingId;

    @Size(max = 255, message = "备注最长 255 字")
    private String remark;
}

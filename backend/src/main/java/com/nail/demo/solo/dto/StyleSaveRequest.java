package com.nail.demo.solo.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建 / 编辑款式入参。
 */
@Data
public class StyleSaveRequest {

    @NotBlank(message = "款式名称不能为空")
    @Size(max = 80, message = "款式名称最长 80 字")
    private String name;

    @Size(max = 500, message = "封面地址最长 500 字")
    private String coverUrl;

    @Min(value = 15, message = "标准时长至少 15 分钟")
    @Max(value = 720, message = "标准时长最多 720 分钟")
    private Integer durationMin;

    @DecimalMin(value = "0", message = "标准价不能为负")
    private BigDecimal price;

    private String materialJson;

    @Size(max = 200, message = "标签最长 200 字")
    private String tags;

    private Integer enabled;
}

package com.nail.demo.solo.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 建单时的单个项目。styleId 有值时，名称/时长/价格未填则用款式带出（落库前做快照）。
 */
@Data
public class BookingItemRequest {

    private Long styleId;

    @Size(max = 80, message = "项目名称最长 80 字")
    private String styleName;

    @Min(value = 15, message = "项目时长至少 15 分钟")
    @Max(value = 720, message = "项目时长最多 720 分钟")
    private Integer durationMin;

    private BigDecimal price;

    private Integer addon;
}

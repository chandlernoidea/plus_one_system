package com.nail.demo.solo.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预约项目返回体：款式名称与价格是建单时的快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingItemResponse {

    private Long id;
    private Long styleId;
    private String styleName;
    private Integer durationMin;
    private BigDecimal price;
    private Integer addon;
    private Integer seq;
}

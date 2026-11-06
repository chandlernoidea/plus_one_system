package com.nail.demo.solo.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

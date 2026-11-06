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
public class StyleResponse {

    private Long id;
    private String name;
    private String coverUrl;
    private Integer durationMin;
    private BigDecimal price;
    private String materialJson;
    private String tags;
    private Integer enabled;
}

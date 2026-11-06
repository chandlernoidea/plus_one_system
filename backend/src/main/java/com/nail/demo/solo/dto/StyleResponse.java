package com.nail.demo.solo.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 款式返回体：名称、封面、标准时长、标准价、材料清单与标签。
 */
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

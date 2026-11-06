package com.nail.demo.solo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 领料返回体：数量与成本快照（赞助物料为 0）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialUsageResponse {

    private Long id;
    private Long bookingId;
    private Long materialId;
    private String materialName;
    private BigDecimal quantity;

    /** 成本快照：赞助物料为 0。 */
    private BigDecimal costSnapshot;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}

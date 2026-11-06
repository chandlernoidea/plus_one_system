package com.nail.demo.solo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositResponse {

    private Long id;
    private Long bookingId;
    private String type;
    private BigDecimal amount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime happenedAt;

    private String reason;

    /* ---- 下面三个只在财务明细里填，单查看某一单定金时为空 ---- */

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime bookingStartAt;

    private String clientCode;

    private String clientNickname;
}

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
public class MoneyFlowResponse {

    private Long id;
    private String direction;
    private String category;
    private BigDecimal amount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime happenedAt;

    private Long bookingId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime bookingStartAt;

    /** 可回跳预约：带上客户短号与昵称。 */
    private String clientCode;
    private String clientNickname;

    private String remark;
}

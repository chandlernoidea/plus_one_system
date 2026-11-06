package com.nail.demo.solo.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 改期入参：新时间、发起方，以及不满足免费条件时的定金处置选择。
 */
@Data
public class BookingRescheduleRequest {

    @NotNull(message = "新的开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime newStart;

    /** 发起方：CLIENT / OWNER，默认 CLIENT。 */
    private String initiator;

    private String reason;

    /**
     * 不满足免费条件时老板的处理选择：NONE（默认，先不改期，只返回提示）/
     * FREE（仍免费）/ REFUND（退定）/ FORFEIT（没收）。
     */
    private String depositDecision;
}

package com.nail.demo.solo.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 待通知清单里的一条：顺延 / 改期后还没跟客户说的变更。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingChangeResponse {

    private Long id;
    private Long bookingId;

    /** RESCHEDULE / SHIFT / JUMP。 */
    private String type;

    private String clientCode;
    private String clientNickname;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime oldStart;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime newStart;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    private String remark;
}

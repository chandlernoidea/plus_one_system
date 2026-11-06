package com.nail.demo.solo.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingShiftPlanResponse {

    /** 影响 N 单。 */
    private int count;

    /** 仅 apply 时为 true；preview 恒为 false。 */
    private boolean applied;

    private List<ShiftItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShiftItem {

        private Long bookingId;
        private String clientCode;
        private String clientNickname;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime oldStart;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime newStart;
    }
}

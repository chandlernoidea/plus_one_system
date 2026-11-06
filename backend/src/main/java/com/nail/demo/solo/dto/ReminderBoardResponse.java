package com.nail.demo.solo.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 「今天要发的微信」：昨日未确认 / 今日 / 明日，每条带一句可直接复制的话术。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderBoardResponse {

    private List<Reminder> yesterday;
    private List<Reminder> today;
    private List<Reminder> tomorrow;

    public int total() {
        return yesterday.size() + today.size() + tomorrow.size();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reminder {

        private Long bookingId;

        private String clientCode;
        private String clientNickname;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime startAt;

        private String status;

        /** 这单做什么，用于话术与原样展示。 */
        private String styles;

        /** 可直接发微信的整句。 */
        private String script;
    }
}

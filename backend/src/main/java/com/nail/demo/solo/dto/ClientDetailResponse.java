package com.nail.demo.solo.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户详情：档案 + 历史预约 + 做过的款式。
 *
 * <p>「常用色号」放在档案里手记，不从这里推导——历史单里没有色号字段，
 * 硬从款式名猜会编数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDetailResponse {

    private ClientResponse client;

    /** 历史预约，按开始时间倒序。 */
    private List<BookingResponse> bookings;

    /** 做过的款式与次数，按次数倒序。 */
    private List<StyleUsage> styleUsage;

    private int totalBookings;

    private int completedBookings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StyleUsage {
        private Long styleId;
        private String styleName;
        private int times;
    }
}

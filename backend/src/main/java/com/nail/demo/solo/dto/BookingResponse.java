package com.nail.demo.solo.dto;

import java.math.BigDecimal;
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
public class BookingResponse {

    private Long id;
    private Long clientId;

    /** 客户已删除时仍回显，前端据此标「客户已删除」。 */
    private String clientCode;
    private String clientNickname;
    private Boolean clientDeleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;

    private Integer plannedMinutes;
    private Integer bufferMinutes;
    private String status;
    private String source;
    private String groupId;
    private String remark;

    /** 项目价格合计。 */
    private BigDecimal totalPrice;

    /** 定金状态由流水推导：NONE / PAID / OFFSET / REFUNDED / FORFEITED。 */
    private String depositState;

    /** 已收定金合计。 */
    private BigDecimal depositCollected;

    /** 定金可退余额 = 收取 − 抵扣 − 退还 − 没收。 */
    private BigDecimal depositBalance;

    private List<BookingItemResponse> items;
}

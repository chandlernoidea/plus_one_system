package com.nail.demo.solo.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 取消预约的入参：原因，以及定金怎么处理（退定 / 没收 / 先不动）。
 */
@Data
public class BookingCancelRequest {

    /** 取消原因：客户原因 / 店铺原因 / 纠纷，老板让位时必填。 */
    @Size(max = 200, message = "取消原因最长 200 字")
    private String reason;

    /** 取消时的定金处理：NONE（默认）/ REFUND / FORFEIT，用于「退定并撤销」。 */
    private String depositDecision;
}

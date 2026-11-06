package com.nail.demo.solo.common;

/**
 * 定金状态（由流水推导，不落库）。
 */
public final class SoloDepositState {

    /** 未收。 */
    public static final String NONE = "NONE";
    /** 已收。 */
    public static final String PAID = "PAID";
    /** 已抵扣尾款。 */
    public static final String OFFSET = "OFFSET";
    /** 已退还。 */
    public static final String REFUNDED = "REFUNDED";
    /** 已没收。 */
    public static final String FORFEITED = "FORFEITED";

    private SoloDepositState() {
    }
}

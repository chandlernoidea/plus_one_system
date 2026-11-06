package com.nail.demo.solo.common;

/**
 * 定金流水类型常量，四条严格分开。
 */
public final class SoloDepositFlowType {

    /** 收取。 */
    public static final String COLLECT = "COLLECT";
    /** 抵扣（服务完成时冲抵尾款）。 */
    public static final String OFFSET = "OFFSET";
    /** 退还。 */
    public static final String REFUND = "REFUND";
    /** 没收。 */
    public static final String FORFEIT = "FORFEIT";

    private SoloDepositFlowType() {
    }
}

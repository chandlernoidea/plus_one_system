package com.nail.demo.solo.common;

/**
 * 变更记录里的定金处理结论（solo_booking_change.deposit_action）。
 *
 * <p>这里只记「结论」；实际金额一律落 solo_deposit_flow，不用状态推断金额。
 */
public final class SoloChangeDepositAction {

    /** 未处理，待老板决定。 */
    public static final String NONE = "NONE";
    /** 免费改期，定金不动。 */
    public static final String FREE = "FREE";
    /** 退还定金。 */
    public static final String REFUND = "REFUND";
    /** 没收定金。 */
    public static final String FORFEIT = "FORFEIT";

    private SoloChangeDepositAction() {
    }
}

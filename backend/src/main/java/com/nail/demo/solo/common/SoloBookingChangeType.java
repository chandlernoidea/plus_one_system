package com.nail.demo.solo.common;

/**
 * 预约变更类型常量（solo_booking_change.type）。
 */
public final class SoloBookingChangeType {

    /** 改期：同一单换时间。 */
    public static final String RESCHEDULE = "RESCHEDULE";
    /** 顺延：平移后续预约。 */
    public static final String SHIFT = "SHIFT";
    /** 插队：插入释放出来的空档。 */
    public static final String JUMP = "JUMP";

    private SoloBookingChangeType() {
    }
}

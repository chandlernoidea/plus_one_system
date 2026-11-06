package com.nail.demo.solo.common;

/**
 * 预约状态事件动作常量（solo_booking_event.action）。
 */
public final class SoloBookingEventAction {

    public static final String CREATE = "CREATE";
    public static final String CONFIRM = "CONFIRM";
    public static final String ARRIVE = "ARRIVE";
    public static final String START = "START";
    public static final String COMPLETE = "COMPLETE";
    public static final String RESCHEDULE = "RESCHEDULE";
    public static final String SHIFT = "SHIFT";
    public static final String CANCEL = "CANCEL";
    public static final String NO_SHOW = "NO_SHOW";

    private SoloBookingEventAction() {
    }
}

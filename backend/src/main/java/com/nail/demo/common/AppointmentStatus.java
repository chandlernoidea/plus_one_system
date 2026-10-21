package com.nail.demo.common;

/**
 * 预约状态常量。
 */
public final class AppointmentStatus {

    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String REJECTED = "REJECTED";
    public static final String EXPIRED = "EXPIRED";
    public static final String CANCELLED = "CANCELLED";
    public static final String RESCHEDULING = "RESCHEDULING";
    public static final String IN_SERVICE = "IN_SERVICE";
    public static final String COMPLETED = "COMPLETED";
    public static final String NO_SHOW = "NO_SHOW";

    private AppointmentStatus() {
    }
}

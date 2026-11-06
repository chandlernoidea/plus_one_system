package com.nail.demo.solo.common;

/**
 * 预约状态常量。
 *
 * <p>主链路：线索 → 待定金 → 已定档 → 到店 → 服务中 → 已完成。
 * <p>分支：已改期、已取消、爽约。
 *
 * <p>改期采用「原单原地改时间 + booking_change 留痕」，状态不进 RESCHEDULED；
 * RESCHEDULED 保留给「原单作废保留、另开新单」的追溯场景（如退定并撤销后重排）。
 */
public final class SoloBookingStatus {

    public static final String LEAD = "LEAD";
    public static final String WAIT_DEPOSIT = "WAIT_DEPOSIT";
    public static final String BOOKED = "BOOKED";
    public static final String ARRIVED = "ARRIVED";
    public static final String IN_SERVICE = "IN_SERVICE";
    public static final String COMPLETED = "COMPLETED";
    public static final String RESCHEDULED = "RESCHEDULED";
    public static final String CANCELLED = "CANCELLED";
    public static final String NO_SHOW = "NO_SHOW";

    private SoloBookingStatus() {
    }

    /** 状态中文名，用于提示文案。 */
    public static String label(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case LEAD -> "线索";
            case WAIT_DEPOSIT -> "待定金";
            case BOOKED -> "已定档";
            case ARRIVED -> "到店";
            case IN_SERVICE -> "服务中";
            case COMPLETED -> "已完成";
            case RESCHEDULED -> "已改期";
            case CANCELLED -> "已取消";
            case NO_SHOW -> "爽约";
            default -> status;
        };
    }
}

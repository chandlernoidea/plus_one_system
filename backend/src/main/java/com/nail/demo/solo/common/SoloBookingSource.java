package com.nail.demo.solo.common;

/**
 * 预约来源常量。
 */
public final class SoloBookingSource {

    /** 常规建单。 */
    public static final String NORMAL = "NORMAL";
    /** 微信约。 */
    public static final String WECHAT = "WECHAT";
    /** 小红书约。 */
    public static final String XHS = "XHS";
    /** 路过 / 到店现约。 */
    public static final String WALKIN = "WALKIN";
    /** 插队（插入取消 / 改期释放的空档）。 */
    public static final String JUMP = "JUMP";

    private SoloBookingSource() {
    }
}

package com.nail.demo.solo.common;

/**
 * 物料采购状态常量：待采购 → 已下单 → 在途 → 已到货 → 已备好。
 */
public final class SoloPurchaseStatus {

    public static final String TODO = "TODO";
    public static final String ORDERED = "ORDERED";
    public static final String SHIPPING = "SHIPPING";
    public static final String ARRIVED = "ARRIVED";
    public static final String READY = "READY";

    private SoloPurchaseStatus() {
    }
}

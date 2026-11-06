package com.nail.demo.solo.common;

/**
 * 营业安排类型常量（solo_slot_block.type）。
 */
public final class SoloSlotType {

    /** 营业。 */
    public static final String OPEN = "OPEN";
    /** 休息。 */
    public static final String REST = "REST";
    /** 外出。 */
    public static final String OUT = "OUT";
    /** 机动位：留给插队 / 加单的弹性时段。 */
    public static final String FLEX = "FLEX";

    private SoloSlotType() {
    }

    /**
     * 是否可供排单（营业与机动位可排）。
     */
    public static boolean bookable(String type) {
        return OPEN.equals(type) || FLEX.equals(type);
    }
}

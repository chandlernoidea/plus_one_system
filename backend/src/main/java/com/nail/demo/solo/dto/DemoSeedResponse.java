package com.nail.demo.solo.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一键演示数据的结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoSeedResponse {

    /** false 表示库里已经有演示数据，这次没重复建。 */
    private boolean created;

    private int clients;
    private int styles;
    private int bookings;

    private List<String> notes;
}

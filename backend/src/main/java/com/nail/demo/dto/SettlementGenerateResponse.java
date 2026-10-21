package com.nail.demo.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementGenerateResponse {

    private String month;
    private List<SettlementResponse> generated;
    private List<String> skipped;
}

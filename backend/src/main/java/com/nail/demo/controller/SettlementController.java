package com.nail.demo.controller;

import java.util.List;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.dto.SettlementGenerateResponse;
import com.nail.demo.dto.SettlementResponse;
import com.nail.demo.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @PostMapping("/generate")
    public ApiResponse<SettlementGenerateResponse> generate(@RequestParam("month") String month) {
        return ApiResponse.success(settlementService.generate(month));
    }

    @GetMapping
    public ApiResponse<List<SettlementResponse>> listByMonth(
            @RequestParam("month") String month) {
        return ApiResponse.success(settlementService.listByMonth(month));
    }

    @GetMapping("/my")
    public ApiResponse<List<SettlementResponse>> my() {
        return ApiResponse.success(settlementService.listMy());
    }

    @PostMapping("/{id}/revoke")
    public ApiResponse<Void> revoke(@PathVariable Long id) {
        settlementService.revoke(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/settle")
    public ApiResponse<Void> settle(@PathVariable Long id) {
        settlementService.settle(id);
        return ApiResponse.success(null);
    }
}

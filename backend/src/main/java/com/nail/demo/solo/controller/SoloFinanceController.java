package com.nail.demo.solo.controller;

import java.util.List;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.solo.dto.DepositResponse;
import com.nail.demo.solo.dto.FinanceSummaryResponse;
import com.nail.demo.solo.dto.MoneyFlowRequest;
import com.nail.demo.solo.dto.MoneyFlowResponse;
import com.nail.demo.solo.service.FinanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/solo/finance")
@RequiredArgsConstructor
public class SoloFinanceController {

    private final FinanceService financeService;

    /** 月度概览：收入、支出、净收入、定金没收额 + 对账结果。 */
    @GetMapping("/summary")
    public ApiResponse<FinanceSummaryResponse> summary(
            @RequestParam(required = false) String month) {
        return ApiResponse.success(financeService.summary(month));
    }

    /** 收入 / 支出明细，direction 传 IN 或 OUT。 */
    @GetMapping("/flows")
    public ApiResponse<List<MoneyFlowResponse>> flows(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String direction) {
        return ApiResponse.success(financeService.flows(month, direction));
    }

    /** 定金明细（第三本账）。 */
    @GetMapping("/deposits")
    public ApiResponse<List<DepositResponse>> deposits(
            @RequestParam(required = false) String month) {
        return ApiResponse.success(financeService.deposits(month));
    }

    @PostMapping("/flows")
    public ApiResponse<MoneyFlowResponse> record(@Valid @RequestBody MoneyFlowRequest request) {
        return ApiResponse.success(financeService.record(request));
    }
}

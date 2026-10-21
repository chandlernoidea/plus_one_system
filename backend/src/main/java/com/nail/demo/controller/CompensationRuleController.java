package com.nail.demo.controller;

import java.util.List;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.dto.CompensationRuleRequest;
import com.nail.demo.dto.CompensationRuleResponse;
import com.nail.demo.service.CompensationRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/compensation-rules")
@RequiredArgsConstructor
public class CompensationRuleController {

    private final CompensationRuleService compensationRuleService;

    @PostMapping
    public ApiResponse<CompensationRuleResponse> save(
            @Valid @RequestBody CompensationRuleRequest request) {
        return ApiResponse.success(compensationRuleService.createOrUpdate(request));
    }

    @GetMapping
    public ApiResponse<List<CompensationRuleResponse>> list() {
        return ApiResponse.success(compensationRuleService.listAll());
    }

    @GetMapping("/staff/{staffId}")
    public ApiResponse<CompensationRuleResponse> getByStaff(@PathVariable Long staffId) {
        return ApiResponse.success(compensationRuleService.getByStaffId(staffId));
    }
}

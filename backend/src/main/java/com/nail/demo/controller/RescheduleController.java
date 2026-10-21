package com.nail.demo.controller;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.dto.RescheduleCreateRequest;
import com.nail.demo.dto.RescheduleResponse;
import com.nail.demo.service.RescheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RescheduleController {

    private final RescheduleService rescheduleService;

    @PostMapping("/appointments/{appointmentId}/reschedules")
    public ApiResponse<RescheduleResponse> create(
            @PathVariable Long appointmentId,
            @Valid @RequestBody RescheduleCreateRequest request) {
        return ApiResponse.success(rescheduleService.create(appointmentId, request));
    }

    @PostMapping("/reschedules/{id}/accept")
    public ApiResponse<RescheduleResponse> accept(@PathVariable Long id) {
        return ApiResponse.success(rescheduleService.accept(id));
    }

    @PostMapping("/reschedules/{id}/reject")
    public ApiResponse<RescheduleResponse> reject(@PathVariable Long id) {
        return ApiResponse.success(rescheduleService.reject(id));
    }
}

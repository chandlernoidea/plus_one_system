package com.nail.demo.controller;

import java.util.List;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.dto.AppointmentCompleteRequest;
import com.nail.demo.dto.AppointmentCreateRequest;
import com.nail.demo.dto.AppointmentRejectRequest;
import com.nail.demo.dto.AppointmentResponse;
import com.nail.demo.service.AppointmentLifecycleService;
import com.nail.demo.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentLifecycleService appointmentLifecycleService;

    @PostMapping
    public ApiResponse<AppointmentResponse> create(@Valid @RequestBody AppointmentCreateRequest request) {
        return ApiResponse.success(appointmentService.create(request));
    }

    @GetMapping("/my")
    public ApiResponse<List<AppointmentResponse>> my() {
        return ApiResponse.success(appointmentService.listMy());
    }

    @GetMapping("/staff")
    public ApiResponse<List<AppointmentResponse>> staff(
            @RequestParam(required = false) String status) {
        return ApiResponse.success(appointmentService.listByStaff(status));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<AppointmentResponse> approve(@PathVariable Long id) {
        return ApiResponse.success(appointmentService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<AppointmentResponse> reject(@PathVariable Long id,
                                                   @Valid @RequestBody AppointmentRejectRequest request) {
        return ApiResponse.success(appointmentService.reject(id, request.getReason()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<AppointmentResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(appointmentService.cancel(id));
    }

    @PostMapping("/{id}/start")
    public ApiResponse<AppointmentResponse> start(@PathVariable Long id) {
        return ApiResponse.success(appointmentService.toResponse(
                appointmentLifecycleService.start(id)));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<AppointmentResponse> complete(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentCompleteRequest request) {
        return ApiResponse.success(appointmentService.toResponse(
                appointmentLifecycleService.complete(id, request.getFinishImageUrls())));
    }

    @PostMapping("/{id}/no-show")
    public ApiResponse<AppointmentResponse> noShow(@PathVariable Long id) {
        return ApiResponse.success(appointmentService.toResponse(
                appointmentLifecycleService.noShow(id)));
    }
}

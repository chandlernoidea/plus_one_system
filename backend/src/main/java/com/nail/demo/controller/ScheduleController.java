package com.nail.demo.controller;

import java.time.LocalDate;
import java.util.List;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.common.CurrentUser;
import com.nail.demo.common.CurrentUserHolder;
import com.nail.demo.dto.ScheduleRequest;
import com.nail.demo.dto.ScheduleResponse;
import com.nail.demo.dto.TimeSlotResponse;
import com.nail.demo.service.ScheduleService;
import com.nail.demo.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_STAFF = "STAFF";

    private final ScheduleService scheduleService;
    private final StaffService staffService;

    @GetMapping
    public ApiResponse<List<ScheduleResponse>> list(
            @RequestParam(required = false) Long staffId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(scheduleService.list(staffId, from, to));
    }

    @PostMapping
    public ApiResponse<ScheduleResponse> create(@Valid @RequestBody ScheduleRequest request) {
        requireStaffOrAdmin();
        request.setStaffId(staffService.resolveStaffId(request.getStaffId()));
        return ApiResponse.success(scheduleService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ScheduleResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody ScheduleRequest request) {
        return ApiResponse.success(scheduleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return ApiResponse.success();
    }

    @GetMapping("/slots")
    public ApiResponse<List<TimeSlotResponse>> slots(
            @RequestParam Long staffId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(scheduleService.listSlots(staffId, date));
    }

    private void requireStaffOrAdmin() {
        CurrentUser currentUser = CurrentUserHolder.get();
        if (currentUser == null
                || (!ROLE_STAFF.equals(currentUser.getRoleCode())
                && !ROLE_ADMIN.equals(currentUser.getRoleCode()))) {
            throw new IllegalArgumentException("无权操作");
        }
    }
}

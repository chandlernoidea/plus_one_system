package com.nail.demo.controller;

import java.util.List;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.dto.StaffResponse;
import com.nail.demo.service.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staffs")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @GetMapping
    public ApiResponse<List<StaffResponse>> list() {
        return ApiResponse.success(staffService.listActiveStaffs());
    }
}

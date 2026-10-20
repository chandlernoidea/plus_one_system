package com.nail.demo.controller;

import java.util.List;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.dto.ServiceTypeResponse;
import com.nail.demo.service.ServiceTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service-types")
@RequiredArgsConstructor
public class ServiceTypeController {

    private final ServiceTypeService serviceTypeService;

    @GetMapping
    public ApiResponse<List<ServiceTypeResponse>> list() {
        return ApiResponse.success(serviceTypeService.listActive());
    }
}

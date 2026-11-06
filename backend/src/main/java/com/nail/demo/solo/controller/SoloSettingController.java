package com.nail.demo.solo.controller;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.solo.dto.SettingsResponse;
import com.nail.demo.solo.service.SoloSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/solo/settings")
@RequiredArgsConstructor
public class SoloSettingController {

    private final SoloSettingService settingService;

    @GetMapping
    public ApiResponse<SettingsResponse> get() {
        return ApiResponse.success(settingService.view());
    }

    @PutMapping
    public ApiResponse<SettingsResponse> update(@Valid @RequestBody SettingsResponse request) {
        return ApiResponse.success(settingService.update(request));
    }
}

package com.nail.demo.solo.controller;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.solo.dto.SettingsRequest;
import com.nail.demo.solo.dto.SettingsResponse;
import com.nail.demo.solo.service.SoloSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设置接口：营业时间模板、默认缓冲、定金金额、免费改期阈值。
 */
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
    public ApiResponse<SettingsResponse> update(@Valid @RequestBody SettingsRequest request) {
        return ApiResponse.success(settingService.update(request));
    }
}

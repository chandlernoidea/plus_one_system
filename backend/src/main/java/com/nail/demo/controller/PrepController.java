package com.nail.demo.controller;

import java.util.List;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.dto.PrepCustomerView;
import com.nail.demo.dto.PrepResponse;
import com.nail.demo.dto.PrepTodayResponse;
import com.nail.demo.dto.PrepUpdateRequest;
import com.nail.demo.service.PrepService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/preps")
@RequiredArgsConstructor
public class PrepController {

    private final PrepService prepService;

    @GetMapping("/{appointmentId}")
    public ApiResponse<PrepResponse> get(@PathVariable Long appointmentId) {
        return ApiResponse.success(prepService.getByAppointmentId(appointmentId));
    }

    @PutMapping("/{appointmentId}")
    public ApiResponse<PrepResponse> update(@PathVariable Long appointmentId,
                                            @Valid @RequestBody PrepUpdateRequest request) {
        return ApiResponse.success(prepService.update(appointmentId, request));
    }

    @GetMapping("/{appointmentId}/customer-view")
    public ApiResponse<PrepCustomerView> customerView(@PathVariable Long appointmentId) {
        return ApiResponse.success(prepService.getCustomerView(appointmentId));
    }

    /**
     * 今天起 N 天内的物料工单，默认 7 天。保留原路径，避免旧前端 404。
     */
    @GetMapping("/today")
    public ApiResponse<List<PrepTodayResponse>> today(
            @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success(prepService.listUpcoming(days));
    }
}

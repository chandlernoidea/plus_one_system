package com.nail.demo.solo.controller;

import java.util.List;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.solo.dto.BookingCreateRequest;
import com.nail.demo.solo.dto.BookingResponse;
import com.nail.demo.solo.dto.BookingShiftPlanResponse;
import com.nail.demo.solo.dto.BookingShiftRequest;
import com.nail.demo.solo.dto.PendingChangeResponse;
import com.nail.demo.solo.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 插队与顺延：两个动作都不属于常规建单，单独成资源，避免和 /bookings/{id} 抢路径。
 */
@RestController
@RequestMapping("/api/solo")
@RequiredArgsConstructor
public class SoloScheduleActionController {

    private final BookingService bookingService;

    /** 插队：只允许插进取消 / 改期释放的空档，塞不下直接 409。 */
    @PostMapping("/jumps")
    public ApiResponse<BookingResponse> jump(@Valid @RequestBody BookingCreateRequest request) {
        return ApiResponse.success(bookingService.jump(request));
    }

    /** 只算不改：返回「影响 N 单」清单。 */
    @PostMapping("/shifts/preview")
    public ApiResponse<BookingShiftPlanResponse> previewShift(@Valid @RequestBody BookingShiftRequest request) {
        return ApiResponse.success(bookingService.previewShift(request));
    }

    /** 确认顺延：逐单平移并写待通知记录，不自动通知客户。 */
    @PostMapping("/shifts")
    public ApiResponse<BookingShiftPlanResponse> shift(@Valid @RequestBody BookingShiftRequest request) {
        return ApiResponse.success(bookingService.shift(request));
    }

    /** 待通知清单：顺延 / 改期后还没跟客户说的变更。 */
    @GetMapping("/changes/pending")
    public ApiResponse<List<PendingChangeResponse>> pendingChanges() {
        return ApiResponse.success(bookingService.pendingChanges());
    }

    /** 发完微信点掉一条。 */
    @PostMapping("/changes/{id}/notified")
    public ApiResponse<Void> markNotified(@PathVariable Long id) {
        bookingService.markNotified(id);
        return ApiResponse.success();
    }
}

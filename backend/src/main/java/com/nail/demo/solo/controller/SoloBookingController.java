package com.nail.demo.solo.controller;

import java.time.LocalDateTime;
import java.util.List;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.solo.dto.BookingCancelRequest;
import com.nail.demo.solo.dto.BookingCreateRequest;
import com.nail.demo.solo.dto.BookingRescheduleRequest;
import com.nail.demo.solo.dto.BookingRescheduleResponse;
import com.nail.demo.solo.dto.BookingResponse;
import com.nail.demo.solo.dto.DepositRequest;
import com.nail.demo.solo.dto.DepositSummaryResponse;
import com.nail.demo.solo.dto.MaterialUsageRequest;
import com.nail.demo.solo.dto.MaterialUsageResponse;
import com.nail.demo.solo.service.BookingService;
import com.nail.demo.solo.service.DepositService;
import com.nail.demo.solo.service.MaterialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/solo/bookings")
@RequiredArgsConstructor
public class SoloBookingController {

    private final BookingService bookingService;
    private final DepositService depositService;
    private final MaterialService materialService;

    @PostMapping
    public ApiResponse<BookingResponse> create(@Valid @RequestBody BookingCreateRequest request) {
        return ApiResponse.success(bookingService.create(request));
    }

    @GetMapping
    public ApiResponse<List<BookingResponse>> list(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to) {
        return ApiResponse.success(bookingService.list(from, to));
    }

    @GetMapping("/{id}")
    public ApiResponse<BookingResponse> get(@PathVariable Long id) {
        return ApiResponse.success(bookingService.get(id));
    }

    @GetMapping("/client/{clientId}")
    public ApiResponse<List<BookingResponse>> listByClient(@PathVariable Long clientId) {
        return ApiResponse.success(bookingService.listByClient(clientId));
    }

    /** 改期：不满足免费条件时返回 needDepositDeduction=true，等老板选定金处理方式再调一次。 */
    @PostMapping("/{id}/reschedule")
    public ApiResponse<BookingRescheduleResponse> reschedule(@PathVariable Long id,
                                                             @Valid @RequestBody BookingRescheduleRequest request) {
        return ApiResponse.success(bookingService.reschedule(id, request));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<BookingResponse> cancel(@PathVariable Long id,
                                               @RequestBody(required = false) BookingCancelRequest request) {
        return ApiResponse.success(bookingService.cancel(id, request == null ? new BookingCancelRequest() : request));
    }

    @PostMapping("/{id}/arrive")
    public ApiResponse<BookingResponse> arrive(@PathVariable Long id) {
        return ApiResponse.success(bookingService.arrive(id));
    }

    @PostMapping("/{id}/start")
    public ApiResponse<BookingResponse> start(@PathVariable Long id) {
        return ApiResponse.success(bookingService.start(id));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<BookingResponse> complete(@PathVariable Long id) {
        return ApiResponse.success(bookingService.complete(id));
    }

    @PostMapping("/{id}/no-show")
    public ApiResponse<BookingResponse> noShow(@PathVariable Long id) {
        return ApiResponse.success(bookingService.noShow(id));
    }

    @GetMapping("/{id}/deposits")
    public ApiResponse<DepositSummaryResponse> deposits(@PathVariable Long id) {
        return ApiResponse.success(depositService.summary(id));
    }

    @PostMapping("/{id}/deposits")
    public ApiResponse<DepositSummaryResponse> addDeposit(@PathVariable Long id,
                                                          @Valid @RequestBody DepositRequest request) {
        return ApiResponse.success(depositService.add(id, request));
    }

    @GetMapping("/{id}/materials")
    public ApiResponse<List<MaterialUsageResponse>> materials(@PathVariable Long id) {
        return ApiResponse.success(materialService.usages(id));
    }

    @PostMapping("/{id}/materials")
    public ApiResponse<MaterialUsageResponse> addMaterial(@PathVariable Long id,
                                                          @Valid @RequestBody MaterialUsageRequest request) {
        return ApiResponse.success(materialService.addUsage(id, request));
    }
}

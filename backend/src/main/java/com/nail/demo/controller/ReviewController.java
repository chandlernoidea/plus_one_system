package com.nail.demo.controller;

import java.util.List;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.dto.ReviewCreateRequest;
import com.nail.demo.dto.ReviewResponse;
import com.nail.demo.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/appointments/{appointmentId}/reviews")
    public ApiResponse<ReviewResponse> create(
            @PathVariable Long appointmentId,
            @Valid @RequestBody ReviewCreateRequest request) {
        return ApiResponse.success(reviewService.create(appointmentId, request));
    }

    @GetMapping("/reviews")
    public ApiResponse<List<ReviewResponse>> list() {
        return ApiResponse.success(reviewService.listForCurrentUser());
    }
}

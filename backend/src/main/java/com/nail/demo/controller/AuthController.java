package com.nail.demo.controller;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.dto.LoginRequest;
import com.nail.demo.dto.LoginResponse;
import com.nail.demo.dto.UserInfoResponse;
import com.nail.demo.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request.getUsername(), request.getPassword()));
    }

    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> me() {
        return ApiResponse.success(authService.getCurrentUser());
    }
}

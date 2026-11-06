package com.nail.demo.solo.controller;

import com.nail.demo.common.ApiResponse;
import com.nail.demo.solo.dto.AccountResponse;
import com.nail.demo.solo.dto.AccountUpdateRequest;
import com.nail.demo.solo.service.SoloAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号接口：查看当前账号、改登录名、改密码。
 *
 * <p>改成功后旧的 token 立即失效（token_version +1），前端需要重新登录。
 */
@RestController
@RequestMapping("/api/solo/account")
@RequiredArgsConstructor
public class SoloAccountController {

    private final SoloAccountService accountService;

    @GetMapping
    public ApiResponse<AccountResponse> get() {
        return ApiResponse.success(accountService.view());
    }

    @PutMapping
    public ApiResponse<AccountResponse> update(@Valid @RequestBody AccountUpdateRequest request) {
        return ApiResponse.success(accountService.update(request));
    }
}

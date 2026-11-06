package com.nail.demo.solo.exception;

import com.nail.demo.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * solo 专用异常处理：冲突要回 HTTP 409 并带冲突单摘要。
 *
 * <p>只处理 {@link SoloConflictException}，其余异常仍由 GlobalExceptionHandler 兜底。
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SoloExceptionHandler {

    @ExceptionHandler(SoloConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Object> handleConflict(SoloConflictException e) {
        log.warn("时段冲突: {}", e.getMessage());
        return new ApiResponse<>(409, e.getMessage(), e.getDetail());
    }
}

package com.nail.demo.solo.exception;

import com.nail.demo.exception.BusinessException;
import lombok.Getter;

/**
 * 时段冲突（HTTP 409）。响应体里额外带冲突单摘要，不静默覆盖。
 */
@Getter
public class SoloConflictException extends BusinessException {

    private final transient Object detail;

    public SoloConflictException(String message, Object detail) {
        super(409, message);
        this.detail = detail;
    }
}

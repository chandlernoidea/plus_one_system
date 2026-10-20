package com.nail.demo.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户上下文，由 JWT 过滤器写入 CurrentUserHolder。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUser {

    private Long userId;
    private String username;
    private String realName;
    private String roleCode;
}

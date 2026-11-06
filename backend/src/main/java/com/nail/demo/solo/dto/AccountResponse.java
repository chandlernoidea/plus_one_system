package com.nail.demo.solo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账号信息：单人版只有老板一个账号，这里回显登录名与角色。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    private String username;

    private String realName;

    private String roleCode;
}

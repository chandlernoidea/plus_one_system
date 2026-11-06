package com.nail.demo.solo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 改账号 / 改密码的入参。
 *
 * <p>两个新值都留空就是「没改」；改任一项都要求先验证当前密码，
 * 防止拿到别人电脑的人直接改掉。
 */
@Data
public class AccountUpdateRequest {

    @NotBlank(message = "请输入当前密码")
    private String currentPassword;

    /** 留空表示不改登录名。 */
    @Size(max = 50, message = "登录名最多 50 个字符")
    private String newUsername;

    /** 留空表示不改密码。 */
    @Size(max = 64, message = "密码最多 64 个字符")
    private String newPassword;
}

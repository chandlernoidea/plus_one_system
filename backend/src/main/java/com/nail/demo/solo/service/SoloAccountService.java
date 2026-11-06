package com.nail.demo.solo.service;

import com.nail.demo.common.CurrentUser;
import com.nail.demo.common.CurrentUserHolder;
import com.nail.demo.entity.SysUser;
import com.nail.demo.exception.BusinessException;
import com.nail.demo.repository.SysUserRepository;
import com.nail.demo.solo.dto.AccountResponse;
import com.nail.demo.solo.dto.AccountUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 账号自助：改登录名、改密码。
 *
 * <p>改完之后把 {@code token_version} 加一，之前签发的 JWT 立即失效——
 * 无状态令牌没有别的吊销手段，这是最小代价的做法。前端随后要重新登录。
 */
@Service
@RequiredArgsConstructor
public class SoloAccountService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final String USERNAME_PATTERN = "^[A-Za-z0-9_.-]{3,50}$";

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public AccountResponse view() {
        CurrentUser current = currentUser();
        return AccountResponse.builder()
                .username(current.getUsername())
                .realName(current.getRealName())
                .roleCode(current.getRoleCode())
                .build();
    }

    @Transactional
    public AccountResponse update(AccountUpdateRequest request) {
        CurrentUser current = currentUser();
        SysUser user = sysUserRepository.findById(current.getUserId())
                .orElseThrow(() -> new BusinessException(404, "账号不存在"));

        if (!StringUtils.hasText(request.getCurrentPassword())
                || !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("当前密码不对");
        }

        String newUsername = StringUtils.hasText(request.getNewUsername())
                ? request.getNewUsername().trim() : null;
        String newPassword = StringUtils.hasText(request.getNewPassword())
                ? request.getNewPassword() : null;

        boolean usernameChanged = newUsername != null && !newUsername.equals(user.getUsername());
        boolean passwordChanged = newPassword != null;

        if (!usernameChanged && !passwordChanged) {
            throw new BusinessException("没有需要修改的内容");
        }

        if (usernameChanged) {
            if (!newUsername.matches(USERNAME_PATTERN)) {
                throw new BusinessException("登录名只能用字母、数字、下划线、点或短横线，3–50 位");
            }
            sysUserRepository.findByUsername(newUsername).ifPresent(existing -> {
                throw new BusinessException("这个登录名已经被占用了");
            });
            user.setUsername(newUsername);
        }

        if (passwordChanged) {
            if (newPassword.length() < MIN_PASSWORD_LENGTH) {
                throw new BusinessException("新密码至少 %d 位".formatted(MIN_PASSWORD_LENGTH));
            }
            if (passwordEncoder.matches(newPassword, user.getPassword())) {
                throw new BusinessException("新密码不能和当前密码一样");
            }
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        // 任何一项改动都让旧令牌失效
        int version = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        user.setTokenVersion(version + 1);
        sysUserRepository.save(user);

        return AccountResponse.builder()
                .username(user.getUsername())
                .realName(user.getRealName())
                .roleCode(current.getRoleCode())
                .build();
    }

    private CurrentUser currentUser() {
        CurrentUser current = CurrentUserHolder.get();
        if (current == null) {
            throw new BusinessException(401, "登录已失效，请重新登录");
        }
        return current;
    }
}

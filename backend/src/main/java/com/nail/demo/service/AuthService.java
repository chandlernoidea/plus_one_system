package com.nail.demo.service;

import java.util.List;

import com.nail.demo.common.CurrentUser;
import com.nail.demo.common.CurrentUserHolder;
import com.nail.demo.config.JwtUtil;
import com.nail.demo.dto.LoginResponse;
import com.nail.demo.dto.UserInfoResponse;
import com.nail.demo.entity.SysRole;
import com.nail.demo.entity.SysUser;
import com.nail.demo.entity.SysUserRole;
import com.nail.demo.exception.BusinessException;
import com.nail.demo.repository.SysRoleRepository;
import com.nail.demo.repository.SysUserRepository;
import com.nail.demo.repository.SysUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final SysUserRepository sysUserRepository;
    private final SysUserRoleRepository sysUserRoleRepository;
    private final SysRoleRepository sysRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponse login(String username, String password) {
        SysUser user = sysUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        List<SysUserRole> userRoles = sysUserRoleRepository.findByUserId(user.getId());
        if (userRoles.isEmpty()) {
            throw new IllegalArgumentException("用户未分配角色");
        }

        SysUserRole userRole = userRoles.get(0);
        SysRole role = sysRoleRepository.findById(userRole.getRoleId())
                .orElseThrow(() -> new IllegalArgumentException("用户角色不存在"));

        String roleCode = role.getRoleCode();
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), roleCode);

        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .roleCode(roleCode)
                .build();
    }

    public UserInfoResponse getCurrentUser() {
        CurrentUser currentUser = CurrentUserHolder.get();
        if (currentUser == null) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        return UserInfoResponse.builder()
                .userId(currentUser.getUserId())
                .username(currentUser.getUsername())
                .realName(currentUser.getRealName())
                .roleCode(currentUser.getRoleCode())
                .build();
    }
}

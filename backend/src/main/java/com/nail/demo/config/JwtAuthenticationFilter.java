package com.nail.demo.config;

import java.io.IOException;
import java.util.List;

import com.nail.demo.common.CurrentUser;
import com.nail.demo.common.CurrentUserHolder;
import com.nail.demo.entity.SysUser;
import com.nail.demo.repository.SysUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final SysUserRepository sysUserRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CurrentUserHolder.clear();

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            if (StringUtils.hasText(token)) {
                authenticate(request, token);
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 请求线程可能被复用，必须清理 ThreadLocal，避免用户信息串用。
            CurrentUserHolder.clear();
        }
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            Claims claims = jwtUtil.parseToken(token);
            String username = claims.getSubject();
            Object userIdClaim = claims.get("userId");
            Object roleCodeClaim = claims.get("roleCode");
            Long userId = userIdClaim instanceof Number ? ((Number) userIdClaim).longValue() : null;
            String roleCode = roleCodeClaim == null ? null : String.valueOf(roleCodeClaim);

            if (userId == null || !StringUtils.hasText(username) || !StringUtils.hasText(roleCode)) {
                throw new IllegalArgumentException("JWT 缺少必要信息");
            }

            SysUser user = sysUserRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("JWT 用户不存在"));

            CurrentUser currentUser = CurrentUser.builder()
                    .userId(user.getId())
                    .username(username)
                    .realName(user.getRealName())
                    .roleCode(roleCode)
                    .build();
            CurrentUserHolder.set(currentUser);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            currentUser,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + roleCode)));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            CurrentUserHolder.clear();
            log.debug("JWT 解析失败: {}", e.getMessage());
        }
    }
}

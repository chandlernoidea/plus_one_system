package com.nail.demo.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.nail.demo.common.CurrentUser;
import com.nail.demo.common.CurrentUserHolder;
import com.nail.demo.dto.StaffResponse;
import com.nail.demo.entity.StaffProfile;
import com.nail.demo.entity.SysUser;
import com.nail.demo.repository.StaffProfileRepository;
import com.nail.demo.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StaffService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String ROLE_ADMIN = "ADMIN";

    private final StaffProfileRepository staffProfileRepository;
    private final SysUserRepository sysUserRepository;

    public Long getCurrentStaffId() {
        CurrentUser currentUser = CurrentUserHolder.get();
        if (currentUser == null) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }
        return staffProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("当前用户不是美甲师"))
                .getId();
    }

    public List<StaffResponse> listActiveStaffs() {
        List<StaffProfile> profiles = staffProfileRepository.findByStatus(STATUS_ACTIVE);
        if (profiles.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = profiles.stream().map(StaffProfile::getUserId).toList();
        Map<Long, SysUser> userMap = sysUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        return profiles.stream()
                .map(profile -> {
                    SysUser user = userMap.get(profile.getUserId());
                    return StaffResponse.builder()
                            .staffId(profile.getId())
                            .userId(profile.getUserId())
                            .staffNo(profile.getStaffNo())
                            .realName(user == null ? null : user.getRealName())
                            .bio(profile.getBio())
                            .avatarUrl(profile.getAvatarUrl())
                            .build();
                })
                .toList();
    }

    public Long resolveStaffId(Long requestStaffId) {
        CurrentUser currentUser = CurrentUserHolder.get();
        if (currentUser == null) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }
        if (ROLE_ADMIN.equals(currentUser.getRoleCode()) && requestStaffId != null) {
            return requestStaffId;
        }
        return getCurrentStaffId();
    }
}

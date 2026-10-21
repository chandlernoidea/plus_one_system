package com.nail.demo.service;

import java.time.LocalDate;
import java.util.List;

import com.nail.demo.common.CurrentUser;
import com.nail.demo.common.CurrentUserHolder;
import com.nail.demo.dto.CompensationRuleRequest;
import com.nail.demo.dto.CompensationRuleResponse;
import com.nail.demo.entity.StaffCompensationRule;
import com.nail.demo.entity.StaffProfile;
import com.nail.demo.entity.SysUser;
import com.nail.demo.repository.StaffCompensationRuleRepository;
import com.nail.demo.repository.StaffProfileRepository;
import com.nail.demo.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompensationRuleService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_STAFF = "STAFF";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final StaffCompensationRuleRepository ruleRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final SysUserRepository sysUserRepository;
    private final StaffService staffService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CompensationRuleResponse createOrUpdate(CompensationRuleRequest request) {
        requireAdmin();
        StaffProfile staff = staffProfileRepository.findByIdForUpdate(request.getStaffId())
                .orElseThrow(() -> new IllegalArgumentException("美甲师不存在"));

        StaffCompensationRule rule = ruleRepository.findByStaffId(request.getStaffId())
                .orElseGet(StaffCompensationRule::new);

        rule.setStaffId(request.getStaffId());
        rule.setBaseSalary(request.getBaseSalary());
        rule.setCommissionRate(request.getCommissionRate());
        rule.setGoodReviewBonus(request.getGoodReviewBonus());
        rule.setBadReviewDeduction(request.getBadReviewDeduction());
        rule.setStatus(STATUS_ACTIVE);
        rule.setEffectiveFrom(request.getEffectiveFrom() == null
                ? LocalDate.now()
                : request.getEffectiveFrom());
        ruleRepository.save(rule);
        return toResponse(rule, staff);
    }

    @Transactional(readOnly = true)
    public CompensationRuleResponse getByStaffId(Long staffId) {
        CurrentUser currentUser = requireCurrentUser();
        if (ROLE_STAFF.equals(currentUser.getRoleCode())
                && !staffService.getCurrentStaffId().equals(staffId)) {
            throw new IllegalArgumentException("无权查看其他美甲师的薪酬规则");
        }
        if (!ROLE_ADMIN.equals(currentUser.getRoleCode())
                && !ROLE_STAFF.equals(currentUser.getRoleCode())) {
            throw new IllegalArgumentException("无权操作");
        }

        return ruleRepository.findByStaffIdAndStatus(staffId, STATUS_ACTIVE)
                .map(rule -> toResponse(rule, staffProfileRepository.findById(staffId).orElse(null)))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<CompensationRuleResponse> listAll() {
        requireAdmin();
        return ruleRepository.findByStatus(STATUS_ACTIVE).stream()
                .map(rule -> toResponse(rule, staffProfileRepository.findById(rule.getStaffId()).orElse(null)))
                .toList();
    }

    private CompensationRuleResponse toResponse(StaffCompensationRule rule, StaffProfile staff) {
        SysUser user = staff == null
                ? null
                : sysUserRepository.findById(staff.getUserId()).orElse(null);
        return CompensationRuleResponse.builder()
                .id(rule.getId())
                .staffId(rule.getStaffId())
                .staffName(user == null ? null : user.getRealName())
                .baseSalary(rule.getBaseSalary())
                .commissionRate(rule.getCommissionRate())
                .goodReviewBonus(rule.getGoodReviewBonus())
                .badReviewDeduction(rule.getBadReviewDeduction())
                .status(rule.getStatus())
                .effectiveFrom(rule.getEffectiveFrom())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    private void requireAdmin() {
        CurrentUser currentUser = requireCurrentUser();
        if (!ROLE_ADMIN.equals(currentUser.getRoleCode())) {
            throw new IllegalArgumentException("只有管理员可以配置薪酬规则");
        }
    }

    private CurrentUser requireCurrentUser() {
        CurrentUser currentUser = CurrentUserHolder.get();
        if (currentUser == null) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }
        return currentUser;
    }
}

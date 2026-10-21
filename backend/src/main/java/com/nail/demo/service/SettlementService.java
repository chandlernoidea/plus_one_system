package com.nail.demo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.nail.demo.common.AppointmentStatus;
import com.nail.demo.common.CurrentUser;
import com.nail.demo.common.CurrentUserHolder;
import com.nail.demo.dto.SettlementGenerateResponse;
import com.nail.demo.dto.SettlementResponse;
import com.nail.demo.entity.Appointment;
import com.nail.demo.entity.MonthlySettlement;
import com.nail.demo.entity.Review;
import com.nail.demo.entity.StaffCompensationRule;
import com.nail.demo.entity.StaffProfile;
import com.nail.demo.entity.SysUser;
import com.nail.demo.repository.AppointmentRepository;
import com.nail.demo.repository.MonthlySettlementRepository;
import com.nail.demo.repository.ReviewRepository;
import com.nail.demo.repository.StaffCompensationRuleRepository;
import com.nail.demo.repository.StaffProfileRepository;
import com.nail.demo.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_STAFF = "STAFF";
    private static final String STAFF_STATUS_ACTIVE = "ACTIVE";
    private static final String RULE_STATUS_ACTIVE = "ACTIVE";
    private static final String SETTLEMENT_DRAFT = "DRAFT";
    private static final String SETTLEMENT_SETTLED = "SETTLED";
    private static final String SENTIMENT_GOOD = "GOOD";
    private static final String SENTIMENT_BAD = "BAD";
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MonthlySettlementRepository settlementRepository;
    private final StaffCompensationRuleRepository ruleRepository;
    private final AppointmentRepository appointmentRepository;
    private final ReviewRepository reviewRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final SysUserRepository sysUserRepository;
    private final StaffService staffService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SettlementGenerateResponse generate(String month) {
        requireAdmin();
        YearMonth yearMonth = parseMonth(month);
        LocalDateTime from = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime to = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        List<StaffProfile> staffs = staffProfileRepository.findByStatus(STAFF_STATUS_ACTIVE)
                .stream()
                .sorted(Comparator.comparing(StaffProfile::getId))
                .toList();

        List<SettlementResponse> generated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (StaffProfile staff : staffs) {
            // 逐个锁定美甲师，避免两个管理员同时生成本月数据时重复插入。
            staffProfileRepository.findByIdForUpdate(staff.getId())
                    .orElseThrow(() -> new IllegalArgumentException("美甲师不存在"));
            String staffName = staffName(staff);

            // MVP 不做规则版本化；effective_from 仅记录生效日期，
            // 未结算月份统一使用当前 ACTIVE 规则重算。
            StaffCompensationRule rule = ruleRepository
                    .findByStaffIdAndStatus(staff.getId(), RULE_STATUS_ACTIVE)
                    .orElse(null);
            if (rule == null) {
                skipped.add("美甲师 " + staffName + " 未配置薪酬规则，已跳过");
                continue;
            }

            List<Appointment> completed = appointmentRepository
                    .findByStaffIdAndStatusAndActualEndBetween(
                            staff.getId(), AppointmentStatus.COMPLETED, from, to);
            int completedCount = completed.size();
            BigDecimal totalRevenue = completed.stream()
                    .map(appointment -> appointment.getTotalAmount() == null
                            ? ZERO_MONEY
                            : appointment.getTotalAmount())
                    .reduce(ZERO_MONEY, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal commissionAmount = totalRevenue
                    .multiply(rule.getCommissionRate())
                    .setScale(2, RoundingMode.HALF_UP);

            // 评价归属跟着预约走，按 appointment.actual_end 所在月归属，
            // 不按 review.created_at 归属。
            List<Long> appointmentIds = completed.stream().map(Appointment::getId).toList();
            List<Review> reviews = appointmentIds.isEmpty()
                    ? List.of()
                    : reviewRepository.findByAppointmentIdInAndSentimentIn(
                            appointmentIds, List.of(SENTIMENT_GOOD, SENTIMENT_BAD));
            int goodReviewCount = (int) reviews.stream()
                    .filter(review -> SENTIMENT_GOOD.equals(review.getSentiment()))
                    .count();
            int badReviewCount = (int) reviews.stream()
                    .filter(review -> SENTIMENT_BAD.equals(review.getSentiment()))
                    .count();

            BigDecimal goodReviewBonusAmount = rule.getGoodReviewBonus()
                    .multiply(BigDecimal.valueOf(goodReviewCount))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal badReviewDeductionAmount = rule.getBadReviewDeduction()
                    .multiply(BigDecimal.valueOf(badReviewCount))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal reviewAdjustment = goodReviewBonusAmount
                    .subtract(badReviewDeductionAmount)
                    .setScale(2, RoundingMode.HALF_UP);

            int noShowCount = (int) appointmentRepository
                    .countByStaffIdAndNoShowFlagAndNoShowExemptAndRequestedStartBetween(
                            staff.getId(), 1, 0, from, to);
            BigDecimal noShowDeduction = BigDecimal
                    .valueOf((noShowCount / 3) * 30L)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal baseSalary = rule.getBaseSalary().setScale(2, RoundingMode.HALF_UP);
            BigDecimal finalPayableBeforeFloor = baseSalary
                    .add(commissionAmount)
                    .add(reviewAdjustment)
                    .subtract(noShowDeduction)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal finalPayable = finalPayableBeforeFloor
                    .max(ZERO_MONEY)
                    .setScale(2, RoundingMode.HALF_UP);

            MonthlySettlement settlement = settlementRepository
                    .findByStaffIdAndSettleMonthForUpdate(staff.getId(), month)
                    .orElse(null);
            if (settlement != null && SETTLEMENT_SETTLED.equals(settlement.getStatus())) {
                skipped.add("美甲师 " + staffName + " 该月已结算，已跳过");
                continue;
            }
            if (settlement == null) {
                settlement = new MonthlySettlement();
                settlement.setStaffId(staff.getId());
                settlement.setSettleMonth(month);
            }

            settlement.setBaseSalary(baseSalary);
            settlement.setCompletedCount(completedCount);
            settlement.setTotalRevenue(totalRevenue);
            settlement.setCommissionAmount(commissionAmount);
            settlement.setGoodReviewCount(goodReviewCount);
            settlement.setGoodReviewBonusAmount(goodReviewBonusAmount);
            settlement.setBadReviewCount(badReviewCount);
            settlement.setBadReviewDeductionAmount(badReviewDeductionAmount);
            settlement.setReviewAdjustment(reviewAdjustment);
            settlement.setNoShowCount(noShowCount);
            settlement.setNoShowDeduction(noShowDeduction);
            settlement.setFinalPayable(finalPayable);
            settlement.setFinalPayableBeforeFloor(finalPayableBeforeFloor);
            settlement.setStatus(SETTLEMENT_DRAFT);
            settlement.setSettledAt(null);
            settlementRepository.save(settlement);
            generated.add(toResponse(settlement, staffName));
        }

        return SettlementGenerateResponse.builder()
                .month(month)
                .generated(generated)
                .skipped(skipped)
                .build();
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> listByMonth(String month) {
        requireAdmin();
        parseMonth(month);
        List<MonthlySettlement> settlements = settlementRepository
                .findBySettleMonthOrderByStaffIdAsc(month);
        return toResponses(settlements);
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> listMy() {
        CurrentUser currentUser = requireCurrentUser();
        if (!ROLE_STAFF.equals(currentUser.getRoleCode())) {
            throw new IllegalArgumentException("只有美甲师可以查看自己的结算");
        }
        Long staffId = staffService.getCurrentStaffId();
        return toResponses(settlementRepository.findByStaffIdOrderBySettleMonthDesc(staffId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void revoke(Long id) {
        requireAdmin();
        MonthlySettlement settlement = settlementRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("结算记录不存在"));
        if (!SETTLEMENT_SETTLED.equals(settlement.getStatus())) {
            throw new IllegalArgumentException("只有已结算的记录可以撤销");
        }
        settlement.setStatus(SETTLEMENT_DRAFT);
        settlement.setSettledAt(null);
        settlementRepository.save(settlement);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void settle(Long id) {
        requireAdmin();
        MonthlySettlement settlement = settlementRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("结算记录不存在"));
        if (!SETTLEMENT_DRAFT.equals(settlement.getStatus())) {
            throw new IllegalArgumentException("只有草稿状态的记录可以确认结算");
        }
        settlement.setStatus(SETTLEMENT_SETTLED);
        settlement.setSettledAt(LocalDateTime.now());
        settlementRepository.save(settlement);
    }

    private List<SettlementResponse> toResponses(List<MonthlySettlement> settlements) {
        if (settlements.isEmpty()) {
            return List.of();
        }
        List<Long> staffIds = settlements.stream()
                .map(MonthlySettlement::getStaffId)
                .distinct()
                .toList();
        Map<Long, StaffProfile> staffMap = staffProfileRepository.findAllById(staffIds).stream()
                .collect(Collectors.toMap(StaffProfile::getId, Function.identity()));
        List<Long> userIds = staffMap.values().stream()
                .map(StaffProfile::getUserId)
                .distinct()
                .toList();
        Map<Long, SysUser> userMap = sysUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        return settlements.stream().map(settlement -> {
            StaffProfile staff = staffMap.get(settlement.getStaffId());
            SysUser user = staff == null ? null : userMap.get(staff.getUserId());
            return toResponse(settlement, user == null ? null : user.getRealName());
        }).toList();
    }

    private SettlementResponse toResponse(MonthlySettlement settlement, String staffName) {
        return SettlementResponse.builder()
                .id(settlement.getId())
                .staffId(settlement.getStaffId())
                .staffName(staffName)
                .settleMonth(settlement.getSettleMonth())
                .baseSalary(settlement.getBaseSalary())
                .completedCount(settlement.getCompletedCount())
                .totalRevenue(settlement.getTotalRevenue())
                .commissionAmount(settlement.getCommissionAmount())
                .goodReviewCount(settlement.getGoodReviewCount())
                .goodReviewBonusAmount(settlement.getGoodReviewBonusAmount())
                .badReviewCount(settlement.getBadReviewCount())
                .badReviewDeductionAmount(settlement.getBadReviewDeductionAmount())
                .reviewAdjustment(settlement.getReviewAdjustment())
                .noShowCount(settlement.getNoShowCount())
                .noShowDeduction(settlement.getNoShowDeduction())
                .finalPayable(settlement.getFinalPayable())
                .finalPayableBeforeFloor(settlement.getFinalPayableBeforeFloor())
                .status(settlement.getStatus())
                .settledAt(settlement.getSettledAt())
                .createdAt(settlement.getCreatedAt())
                .updatedAt(settlement.getUpdatedAt())
                .build();
    }

    private String staffName(StaffProfile staff) {
        return sysUserRepository.findById(staff.getUserId())
                .map(user -> user.getRealName() != null ? user.getRealName() : user.getUsername())
                .orElse("美甲师#" + staff.getId());
    }

    private YearMonth parseMonth(String month) {
        if (month == null || !month.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("月份格式必须为 YYYY-MM");
        }
        try {
            return YearMonth.parse(month, MONTH_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("月份格式必须为 YYYY-MM");
        }
    }

    private void requireAdmin() {
        CurrentUser currentUser = requireCurrentUser();
        if (!ROLE_ADMIN.equals(currentUser.getRoleCode())) {
            throw new IllegalArgumentException("只有管理员可以进行结算操作");
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

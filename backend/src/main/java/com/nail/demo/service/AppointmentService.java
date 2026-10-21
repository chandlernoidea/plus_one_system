package com.nail.demo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import com.nail.demo.common.AppointmentStatus;
import com.nail.demo.common.CurrentUser;
import com.nail.demo.common.CurrentUserHolder;
import com.nail.demo.common.RescheduleStatus;
import com.nail.demo.dto.AppointmentCreateRequest;
import com.nail.demo.dto.AppointmentResponse;
import com.nail.demo.entity.Appointment;
import com.nail.demo.entity.AppointmentPrep;
import com.nail.demo.entity.AppointmentReschedule;
import com.nail.demo.entity.AppointmentStatusLog;
import com.nail.demo.entity.Review;
import com.nail.demo.entity.ServiceType;
import com.nail.demo.entity.StaffProfile;
import com.nail.demo.entity.SysUser;
import com.nail.demo.entity.TimeSlot;
import jakarta.persistence.EntityManager;
import com.nail.demo.repository.AppointmentRepository;
import com.nail.demo.repository.AppointmentRescheduleRepository;
import com.nail.demo.repository.AppointmentStatusLogRepository;
import com.nail.demo.repository.ServiceTypeRepository;
import com.nail.demo.repository.StaffProfileRepository;
import com.nail.demo.repository.SysUserRepository;
import com.nail.demo.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final int SLOT_MINUTES = 15;
    private static final int MAX_PENDING_PER_START = 10;
    private static final int CONFLICT_LOOKBACK_HOURS = 24;

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_BOOKED = "BOOKED";

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_STAFF = "STAFF";
    private static final String ROLE_CUSTOMER = "CUSTOMER";

    private static final List<String> OCCUPYING_STATUSES = List.of(
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.IN_SERVICE,
            AppointmentStatus.RESCHEDULING
    );

    private final AppointmentRepository appointmentRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final SysUserRepository sysUserRepository;
    private final StaffService staffService;
    private final AppointmentExpiryService appointmentExpiryService;
    private final AppointmentRescheduleRepository rescheduleRepository;
    private final PrepService prepService;
    private final RescheduleService rescheduleService;
    private final ReviewService reviewService;
    private final AppointmentStatusLogRepository statusLogRepository;
    private final EntityManager entityManager;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse create(AppointmentCreateRequest req) {
        CurrentUser currentUser = requireCurrentUser();
        if (!ROLE_CUSTOMER.equals(currentUser.getRoleCode())) {
            throw new IllegalArgumentException("无权操作");
        }

        LocalDateTime now = LocalDateTime.now();
        if (req.getRequestedStart().isBefore(now)) {
            throw new IllegalArgumentException("预约开始时间不能早于当前时间");
        }

        StaffProfile staff = staffProfileRepository.findByIdForUpdate(req.getStaffId())
                .orElseThrow(() -> new IllegalArgumentException("美甲师不存在"));
        if (!STATUS_ACTIVE.equals(staff.getStatus())) {
            throw new IllegalArgumentException("该美甲师当前不可预约");
        }

        ServiceType serviceType = serviceTypeRepository.findById(req.getServiceTypeId())
                .orElseThrow(() -> new IllegalArgumentException("项目类型不存在"));
        if (!STATUS_ACTIVE.equals(serviceType.getStatus())) {
            throw new IllegalArgumentException("该项目类型当前不可预约");
        }

        Integer duration = serviceType.getEstDurationMax();
        validateDuration(duration);
        LocalDateTime requestedStart = req.getRequestedStart();
        LocalDateTime requestedEnd = requestedStart.plusMinutes(duration);

        List<TimeSlot> slots = findCoveredSlots(staff.getId(), requestedStart, requestedEnd);
        validateSlotWindow(slots, requestedStart, duration,
                "该服务时长内存在不可用时段，请选择其他时间",
                "该服务时长内存在不可用时段，请选择其他时间");

        long pendingCount = appointmentRepository.countByStaffIdAndRequestedStartAndStatus(
                staff.getId(), requestedStart, AppointmentStatus.PENDING);
        if (pendingCount >= MAX_PENDING_PER_START) {
            throw new IllegalArgumentException("该时段申请已满，请选择其他时段");
        }

        Appointment appointment = new Appointment();
        appointment.setCustomerId(currentUser.getUserId());
        appointment.setStaffId(staff.getId());
        appointment.setServiceTypeId(serviceType.getId());
        appointment.setRequestedStart(requestedStart);
        appointment.setEstDurationMinutes(duration);
        appointment.setStatus(AppointmentStatus.PENDING);
        appointment.setExpireAt(now.plusHours(24));
        appointment.setNoShowFlag(0);
        appointmentRepository.save(appointment);

        return toResponse(appointment);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse approve(Long id) {
        CurrentUser currentUser = requireCurrentUser();
        requireStaffOrAdmin(currentUser);

        Appointment snapshot = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        if (!AppointmentStatus.PENDING.equals(snapshot.getStatus())) {
            throw new IllegalArgumentException("当前预约状态不可审批");
        }
        checkStaffPermission(snapshot, currentUser);

        LocalDateTime now = LocalDateTime.now();
        if (isExpired(snapshot, now)) {
            appointmentExpiryService.expireOne(id);
            throw new IllegalArgumentException("该申请已过期");
        }

        staffProfileRepository.findByIdForUpdate(snapshot.getStaffId())
                .orElseThrow(() -> new IllegalArgumentException("美甲师不存在"));
        entityManager.clear();
        Appointment appointment = appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));

        checkStaffPermission(appointment, currentUser);
        if (!AppointmentStatus.PENDING.equals(appointment.getStatus())) {
            throw new IllegalArgumentException("当前预约状态不可审批");
        }
        if (isExpired(appointment, LocalDateTime.now())) {
            throw new IllegalArgumentException("该申请已过期");
        }

        LocalDateTime start = appointment.getRequestedStart();
        LocalDateTime end = start.plusMinutes(appointment.getEstDurationMinutes());

        ensureNoConfirmedOverlap(appointment.getStaffId(), start, end);
        List<TimeSlot> slots = findCoveredSlots(appointment.getStaffId(), start, end);
        validateSlotWindow(slots, start, appointment.getEstDurationMinutes(),
                "该时段已不在可预约范围，请拒绝并让顾客重新选择",
                "该时段已被占用，请先处理冲突或拒绝");

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setConfirmedStart(start);
        appointment.setApprovedBy(currentUser.getUserId());
        appointment.setApprovedAt(now);
        appointmentRepository.save(appointment);

        for (TimeSlot slot : slots) {
            slot.setStatus(STATUS_BOOKED);
        }
        timeSlotRepository.saveAll(slots);
        prepService.createForAppointment(appointment.getId());
        return toResponse(appointment);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse reject(Long id, String reason) {
        CurrentUser currentUser = requireCurrentUser();
        requireStaffOrAdmin(currentUser);
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("拒绝原因不能为空");
        }
        if (reason.length() > 255) {
            throw new IllegalArgumentException("拒绝原因不能超过255个字符");
        }

        Appointment snapshot = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        if (!AppointmentStatus.PENDING.equals(snapshot.getStatus())) {
            throw new IllegalArgumentException("当前预约状态不可拒绝");
        }
        checkStaffPermission(snapshot, currentUser);

        LocalDateTime now = LocalDateTime.now();
        if (isExpired(snapshot, now)) {
            appointmentExpiryService.expireOne(id);
            throw new IllegalArgumentException("该申请已过期");
        }

        staffProfileRepository.findByIdForUpdate(snapshot.getStaffId())
                .orElseThrow(() -> new IllegalArgumentException("美甲师不存在"));
        entityManager.clear();
        Appointment appointment = appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));

        checkStaffPermission(appointment, currentUser);
        if (!AppointmentStatus.PENDING.equals(appointment.getStatus())) {
            throw new IllegalArgumentException("当前预约状态不可拒绝");
        }
        if (isExpired(appointment, LocalDateTime.now())) {
            throw new IllegalArgumentException("该申请已过期");
        }

        appointment.setStatus(AppointmentStatus.REJECTED);
        appointment.setRejectReason(reason.trim());
        appointmentRepository.save(appointment);
        return toResponse(appointment);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse cancel(Long id) {
        CurrentUser currentUser = requireCurrentUser();
        Appointment snapshot = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));

        boolean owner = currentUser.getUserId().equals(snapshot.getCustomerId());
        if (!owner && !ROLE_ADMIN.equals(currentUser.getRoleCode())) {
            throw new IllegalArgumentException("无权取消该预约");
        }
        if (!canCancel(snapshot.getStatus())) {
            throw new IllegalArgumentException("当前预约状态不可取消");
        }

        staffProfileRepository.findByIdForUpdate(snapshot.getStaffId())
                .orElseThrow(() -> new IllegalArgumentException("美甲师不存在"));
        entityManager.clear();
        Appointment appointment = appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));

        if (!canCancel(appointment.getStatus())) {
            throw new IllegalArgumentException("当前预约状态不可取消");
        }

        if (AppointmentStatus.CONFIRMED.equals(appointment.getStatus())
                || AppointmentStatus.RESCHEDULING.equals(appointment.getStatus())) {
            LocalDateTime start = appointment.getConfirmedStart() != null
                    ? appointment.getConfirmedStart()
                    : appointment.getRequestedStart();
            LocalDateTime end = start.plusMinutes(appointment.getEstDurationMinutes());
            List<TimeSlot> slots = findCoveredSlots(appointment.getStaffId(), start, end);
            for (TimeSlot slot : slots) {
                if (STATUS_BOOKED.equals(slot.getStatus())) {
                    slot.setStatus(STATUS_AVAILABLE);
                }
            }
            timeSlotRepository.saveAll(slots);
        }

        rescheduleRepository.findByAppointmentIdAndStatus(id, RescheduleStatus.PENDING)
                .ifPresent(reschedule -> {
                    reschedule.setStatus(RescheduleStatus.CANCELLED);
                    reschedule.setRespondedAt(LocalDateTime.now());
                    rescheduleRepository.save(reschedule);
                });

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);
        prepService.onAppointmentCancelled(appointment.getId());
        return toResponse(appointment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listMy() {
        CurrentUser currentUser = requireCurrentUser();
        if (!ROLE_CUSTOMER.equals(currentUser.getRoleCode())) {
            throw new IllegalArgumentException("无权操作");
        }
        // MVP 阶段逐个组装关联数据，数据量增大后可改为批量查询。
        return appointmentRepository.findByCustomerIdOrderByCreatedAtDesc(currentUser.getUserId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listByStaff(String status) {
        CurrentUser currentUser = requireCurrentUser();
        requireStaffOrAdmin(currentUser);
        boolean hasStatus = StringUtils.hasText(status);

        List<Appointment> appointments;
        if (ROLE_ADMIN.equals(currentUser.getRoleCode())) {
            appointments = hasStatus
                    ? appointmentRepository.findByStatusOrderByRequestedStartAsc(status.trim())
                    : appointmentRepository.findAllByOrderByRequestedStartAsc();
        } else {
            Long staffId = staffService.getCurrentStaffId();
            appointments = hasStatus
                    ? appointmentRepository.findByStaffIdAndStatusOrderByRequestedStartAsc(
                            staffId, status.trim())
                    : appointmentRepository.findByStaffIdOrderByRequestedStartAsc(staffId);
        }

        // MVP 阶段逐个组装关联数据，数据量增大后可改为批量查询。
        return appointments.stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 管理员撤销爽约标记。预约状态仍保持 NO_SHOW 以保留历史，
     * 只把 no_show_exempt 置为 1，结算时不再计爽约扣款。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse exemptNoShow(Long id) {
        CurrentUser currentUser = requireCurrentUser();
        if (!ROLE_ADMIN.equals(currentUser.getRoleCode())) {
            throw new IllegalArgumentException("只有管理员可以撤销爽约");
        }

        Appointment snapshot = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        staffProfileRepository.findByIdForUpdate(snapshot.getStaffId())
                .orElseThrow(() -> new IllegalArgumentException("美甲师不存在"));
        entityManager.clear();
        Appointment appointment = appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));

        if (!AppointmentStatus.NO_SHOW.equals(appointment.getStatus())
                || appointment.getNoShowFlag() == null
                || appointment.getNoShowFlag() != 1) {
            throw new IllegalArgumentException("该预约不是爽约状态");
        }
        if (appointment.getNoShowExempt() != null && appointment.getNoShowExempt() == 1) {
            return toResponse(appointment);
        }

        appointment.setNoShowExempt(1);
        appointmentRepository.save(appointment);

        AppointmentStatusLog statusLog = new AppointmentStatusLog();
        statusLog.setAppointmentId(appointment.getId());
        statusLog.setAction("NO_SHOW_EXEMPT");
        statusLog.setOperatorId(currentUser.getUserId());
        statusLog.setOldExempt(0);
        statusLog.setNewExempt(1);
        statusLog.setRemark("管理员撤销爽约，结算时不再计爽约扣款");
        statusLogRepository.save(statusLog);
        return toResponse(appointment);
    }

    /**
     * 管理员修正已完成预约的订单金额，用于历史数据补录。
     * 已 SETTLED 的结算快照不会因此自动变化，需要撤销结算后重新生成。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse updateTotalAmount(Long id, BigDecimal totalAmount) {
        CurrentUser currentUser = requireCurrentUser();
        if (!ROLE_ADMIN.equals(currentUser.getRoleCode())) {
            throw new IllegalArgumentException("只有管理员可以修正订单金额");
        }
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("订单金额不能小于0");
        }

        Appointment snapshot = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        staffProfileRepository.findByIdForUpdate(snapshot.getStaffId())
                .orElseThrow(() -> new IllegalArgumentException("美甲师不存在"));
        entityManager.clear();
        Appointment appointment = appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));

        if (!AppointmentStatus.COMPLETED.equals(appointment.getStatus())) {
            throw new IllegalArgumentException("只有已完成的预约可以修正订单金额");
        }

        appointment.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        appointmentRepository.save(appointment);

        AppointmentStatusLog statusLog = new AppointmentStatusLog();
        statusLog.setAppointmentId(appointment.getId());
        statusLog.setAction("TOTAL_AMOUNT_UPDATE");
        statusLog.setOperatorId(currentUser.getUserId());
        statusLog.setRemark("管理员修正订单金额为 " + appointment.getTotalAmount());
        statusLogRepository.save(statusLog);
        return toResponse(appointment);
    }

    @Transactional
    public void expirePending() {
        int expiredCount = appointmentRepository.expirePending(
                AppointmentStatus.PENDING,
                AppointmentStatus.EXPIRED,
                LocalDateTime.now());
        log.info("本次过期预约 {} 条", expiredCount);
    }

    public AppointmentResponse toResponse(Appointment appointment) {
        SysUser customer = sysUserRepository.findById(appointment.getCustomerId()).orElse(null);
        StaffProfile staffProfile = staffProfileRepository.findById(appointment.getStaffId()).orElse(null);
        SysUser staffUser = staffProfile == null
                ? null
                : sysUserRepository.findById(staffProfile.getUserId()).orElse(null);
        ServiceType serviceType = serviceTypeRepository.findById(appointment.getServiceTypeId()).orElse(null);
        CurrentUser currentUser = CurrentUserHolder.get();

        AppointmentPrep prep = prepService.findEntityOrNull(appointment.getId());
        com.nail.demo.dto.PrepResponse prepResponse = null;
        com.nail.demo.dto.PrepCustomerView customerPrepView = null;
        if (prep != null && currentUser != null) {
            if (ROLE_ADMIN.equals(currentUser.getRoleCode())
                    || (ROLE_STAFF.equals(currentUser.getRoleCode())
                    && staffProfile != null
                    && currentUser.getUserId().equals(staffProfile.getUserId()))) {
                prepResponse = prepService.toResponse(prep);
            } else if (currentUser.getUserId().equals(appointment.getCustomerId())) {
                customerPrepView = prepService.toCustomerView(prep);
            }
        }

        AppointmentReschedule pending = rescheduleRepository
                .findByAppointmentIdAndStatus(appointment.getId(), RescheduleStatus.PENDING)
                .orElse(null);
        Review review = reviewService.findByAppointmentIdOrNull(appointment.getId());

        return AppointmentResponse.builder()
                .id(appointment.getId())
                .customerId(appointment.getCustomerId())
                .customerName(customer == null
                        ? null
                        : (customer.getRealName() != null ? customer.getRealName() : customer.getUsername()))
                .staffId(appointment.getStaffId())
                .staffName(staffUser == null
                        ? null
                        : (staffUser.getRealName() != null ? staffUser.getRealName() : staffUser.getUsername()))
                .serviceTypeId(appointment.getServiceTypeId())
                .serviceTypeName(serviceType == null ? null : serviceType.getName())
                .requestedStart(appointment.getRequestedStart())
                .confirmedStart(appointment.getConfirmedStart())
                .estDurationMinutes(appointment.getEstDurationMinutes())
                .actualStart(appointment.getActualStart())
                .actualEnd(appointment.getActualEnd())
                .status(appointment.getStatus())
                .rejectReason(appointment.getRejectReason())
                .expireAt(appointment.getExpireAt())
                .approvedAt(appointment.getApprovedAt())
                .totalAmount(appointment.getTotalAmount())
                .noShowFlag(appointment.getNoShowFlag())
                .noShowExempt(appointment.getNoShowExempt())
                .createdAt(appointment.getCreatedAt())
                .prep(prepResponse)
                .pendingReschedule(pending == null ? null : rescheduleService.toResponse(pending))
                .customerPrepView(customerPrepView)
                .finishImageUrls(splitImages(appointment.getFinishImageUrls()))
                .review(review == null ? null : reviewService.toResponse(review))
                .build();
    }

    public boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd,
                            LocalDateTime bStart, LocalDateTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    private List<String> splitImages(String imageUrls) {
        if (!StringUtils.hasText(imageUrls)) {
            return List.of();
        }
        return java.util.Arrays.stream(imageUrls.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private void ensureNoConfirmedOverlap(Long staffId, LocalDateTime start, LocalDateTime end) {
        LocalDateTime lookbackStart = start.minusHours(CONFLICT_LOOKBACK_HOURS);
        List<Appointment> candidates = appointmentRepository
                .findByStaffIdAndStatusInAndConfirmedStartBetween(
                        staffId, OCCUPYING_STATUSES, lookbackStart, end);

        for (Appointment candidate : candidates) {
            if (candidate.getConfirmedStart() == null || candidate.getEstDurationMinutes() == null) {
                continue;
            }
            LocalDateTime candidateEnd = candidate.getConfirmedStart()
                    .plusMinutes(candidate.getEstDurationMinutes());
            if (overlaps(start, end, candidate.getConfirmedStart(), candidateEnd)) {
                throw new IllegalArgumentException("该时间段已被占用，请先处理冲突或拒绝");
            }
        }
    }

    private List<TimeSlot> findCoveredSlots(Long staffId, LocalDateTime start, LocalDateTime end) {
        return timeSlotRepository
                .findByStaffIdAndSlotStartGreaterThanEqualAndSlotStartLessThanOrderBySlotStartAsc(
                        staffId, start, end);
    }

    private void validateSlotWindow(List<TimeSlot> slots, LocalDateTime start, Integer duration,
                                    String missingMessage, String unavailableMessage) {
        validateDuration(duration);
        int expectedCount = duration / SLOT_MINUTES;
        if (slots.size() != expectedCount) {
            throw new IllegalArgumentException(missingMessage);
        }

        for (int i = 0; i < expectedCount; i++) {
            TimeSlot slot = slots.get(i);
            LocalDateTime expectedStart = start.plusMinutes((long) i * SLOT_MINUTES);
            if (!expectedStart.equals(slot.getSlotStart())
                    || !expectedStart.plusMinutes(SLOT_MINUTES).equals(slot.getSlotEnd())) {
                throw new IllegalArgumentException(missingMessage);
            }
            if (!STATUS_AVAILABLE.equals(slot.getStatus())) {
                throw new IllegalArgumentException(unavailableMessage);
            }
        }
    }

    private void validateDuration(Integer duration) {
        if (duration == null || duration <= 0 || duration % SLOT_MINUTES != 0) {
            throw new IllegalArgumentException("项目时长配置异常");
        }
    }

    private boolean isExpired(Appointment appointment, LocalDateTime now) {
        return (appointment.getExpireAt() != null && appointment.getExpireAt().isBefore(now))
                || (appointment.getRequestedStart() != null
                && appointment.getRequestedStart().isBefore(now));
    }

    private boolean canCancel(String status) {
        return AppointmentStatus.PENDING.equals(status)
                || AppointmentStatus.CONFIRMED.equals(status)
                || AppointmentStatus.RESCHEDULING.equals(status);
    }

    private void checkStaffPermission(Appointment appointment, CurrentUser currentUser) {
        if (ROLE_ADMIN.equals(currentUser.getRoleCode())) {
            return;
        }
        if (!ROLE_STAFF.equals(currentUser.getRoleCode())) {
            throw new IllegalArgumentException("无权操作");
        }
        Long currentStaffId = staffService.getCurrentStaffId();
        if (!appointment.getStaffId().equals(currentStaffId)) {
            throw new IllegalArgumentException("无权操作");
        }
    }

    private void requireStaffOrAdmin(CurrentUser currentUser) {
        if (!ROLE_STAFF.equals(currentUser.getRoleCode())
                && !ROLE_ADMIN.equals(currentUser.getRoleCode())) {
            throw new IllegalArgumentException("无权操作");
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

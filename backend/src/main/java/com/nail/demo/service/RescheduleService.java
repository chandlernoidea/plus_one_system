package com.nail.demo.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nail.demo.common.AppointmentStatus;
import com.nail.demo.common.CurrentUser;
import com.nail.demo.common.CurrentUserHolder;
import com.nail.demo.common.RescheduleStatus;
import com.nail.demo.dto.RescheduleCreateRequest;
import com.nail.demo.dto.RescheduleResponse;
import com.nail.demo.entity.Appointment;
import com.nail.demo.entity.AppointmentReschedule;
import com.nail.demo.entity.StaffProfile;
import com.nail.demo.entity.SysUser;
import com.nail.demo.entity.TimeSlot;
import jakarta.persistence.EntityManager;
import com.nail.demo.repository.AppointmentRepository;
import com.nail.demo.repository.AppointmentRescheduleRepository;
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
public class RescheduleService {

    private static final int SLOT_MINUTES = 15;
    private static final int CONFLICT_LOOKBACK_HOURS = 24;
    private static final int EXPIRE_HOURS = 12;

    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_BOOKED = "BOOKED";
    private static final String ROLE_STAFF = "STAFF";
    private static final List<String> OCCUPYING_STATUSES = List.of(
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.IN_SERVICE,
            AppointmentStatus.RESCHEDULING
    );

    private final AppointmentRescheduleRepository rescheduleRepository;
    private final AppointmentRepository appointmentRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final StaffService staffService;
    private final StaffProfileRepository staffProfileRepository;
    private final SysUserRepository sysUserRepository;
    private final RescheduleExpiryService rescheduleExpiryService;
    private final EntityManager entityManager;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RescheduleResponse create(Long appointmentId, RescheduleCreateRequest req) {
        CurrentUser currentUser = requireCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        if (req.getProposedStart() == null || req.getProposedStart().isBefore(now)) {
            throw new IllegalArgumentException("新开始时间不能早于当前时间");
        }

        Appointment snapshot = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        assertProposer(snapshot, currentUser);
        if (!AppointmentStatus.CONFIRMED.equals(snapshot.getStatus())) {
            throw new IllegalArgumentException("只有已确认的预约可以发起改约");
        }
        if (rescheduleRepository.findByAppointmentIdAndStatus(
                appointmentId, RescheduleStatus.PENDING).isPresent()) {
            throw new IllegalArgumentException("该预约已有待处理的改约提案");
        }

        Long staffId = snapshot.getStaffId();
        staffProfileRepository.findByIdForUpdate(staffId)
                .orElseThrow(() -> new IllegalArgumentException("美甲师不存在"));
        entityManager.clear();
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        assertProposer(appointment, currentUser);
        if (!AppointmentStatus.CONFIRMED.equals(appointment.getStatus())) {
            throw new IllegalArgumentException("只有已确认的预约可以发起改约");
        }
        if (rescheduleRepository.findByAppointmentIdAndStatus(
                appointmentId, RescheduleStatus.PENDING).isPresent()) {
            throw new IllegalArgumentException("该预约已有待处理的改约提案");
        }

        LocalDateTime newStart = req.getProposedStart();
        Integer duration = appointment.getEstDurationMinutes();
        validateDuration(duration);
        LocalDateTime newEnd = newStart.plusMinutes(duration);

        ensureNoOccupiedOverlap(staffId, newStart, newEnd, appointment.getId());
        validateNewSlots(appointment, newStart);

        AppointmentReschedule reschedule = new AppointmentReschedule();
        reschedule.setAppointmentId(appointment.getId());
        reschedule.setProposerId(currentUser.getUserId());
        reschedule.setProposedStart(newStart);
        reschedule.setStatus(RescheduleStatus.PENDING);
        reschedule.setExpireAt(now.plusHours(EXPIRE_HOURS));
        reschedule.setReason(StringUtils.hasText(req.getReason()) ? req.getReason().trim() : null);
        rescheduleRepository.save(reschedule);

        appointment.setStatus(AppointmentStatus.RESCHEDULING);
        appointmentRepository.save(appointment);
        return toResponse(reschedule);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RescheduleResponse accept(Long rescheduleId) {
        CurrentUser currentUser = requireCurrentUser();
        AppointmentReschedule snapshot = rescheduleRepository.findById(rescheduleId)
                .orElseThrow(() -> new IllegalArgumentException("改约提案不存在"));
        if (!RescheduleStatus.PENDING.equals(snapshot.getStatus())) {
            throw new IllegalArgumentException("改约提案状态不可接受");
        }
        if (isExpired(snapshot, LocalDateTime.now())) {
            rescheduleExpiryService.expireOne(rescheduleId);
            throw new IllegalArgumentException("改约提案已过期");
        }

        Appointment appointmentSnapshot = appointmentRepository.findById(snapshot.getAppointmentId())
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        lockStaff(appointmentSnapshot.getStaffId());
        entityManager.clear();
        Appointment appointment = appointmentRepository.findByIdForUpdate(snapshot.getAppointmentId())
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        AppointmentReschedule reschedule = rescheduleRepository.findByIdForUpdate(rescheduleId)
                .orElseThrow(() -> new IllegalArgumentException("改约提案不存在"));

        if (!RescheduleStatus.PENDING.equals(reschedule.getStatus())) {
            throw new IllegalArgumentException("改约提案状态不可接受");
        }
        if (!AppointmentStatus.RESCHEDULING.equals(appointment.getStatus())) {
            throw new IllegalArgumentException("预约当前不在改约中");
        }
        if (isExpired(reschedule, LocalDateTime.now())) {
            throw new IllegalArgumentException("改约提案已过期");
        }

        LocalDateTime now = LocalDateTime.now();
        if (reschedule.getProposedStart().isBefore(now)) {
            throw new IllegalArgumentException("改约时间已过，无法接受");
        }
        assertResponder(appointment, reschedule, currentUser);

        Integer duration = appointment.getEstDurationMinutes();
        validateDuration(duration);
        LocalDateTime newStart = reschedule.getProposedStart();
        LocalDateTime newEnd = newStart.plusMinutes(duration);
        ensureNoOccupiedOverlap(appointment.getStaffId(), newStart, newEnd, appointment.getId());
        List<TimeSlot> newSlots = validateNewSlots(appointment, newStart);

        LocalDateTime oldStart = appointment.getConfirmedStart() != null
                ? appointment.getConfirmedStart()
                : appointment.getRequestedStart();
        LocalDateTime oldEnd = oldStart.plusMinutes(duration);
        List<TimeSlot> oldSlots = findCoveredSlots(appointment.getStaffId(), oldStart, oldEnd);

        Map<Long, TimeSlot> changedSlots = new LinkedHashMap<>();
        for (TimeSlot slot : oldSlots) {
            changedSlots.put(slot.getId(), slot);
            if (STATUS_BOOKED.equals(slot.getStatus())) {
                slot.setStatus(STATUS_AVAILABLE);
            }
        }
        for (TimeSlot slot : newSlots) {
            changedSlots.put(slot.getId(), slot);
            slot.setStatus(STATUS_BOOKED);
        }
        timeSlotRepository.saveAll(changedSlots.values());

        appointment.setRequestedStart(newStart);
        appointment.setConfirmedStart(newStart);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointmentRepository.save(appointment);

        reschedule.setStatus(RescheduleStatus.ACCEPTED);
        reschedule.setRespondedAt(now);
        rescheduleRepository.save(reschedule);
        return toResponse(reschedule);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RescheduleResponse reject(Long rescheduleId) {
        CurrentUser currentUser = requireCurrentUser();
        AppointmentReschedule snapshot = rescheduleRepository.findById(rescheduleId)
                .orElseThrow(() -> new IllegalArgumentException("改约提案不存在"));
        if (!RescheduleStatus.PENDING.equals(snapshot.getStatus())) {
            throw new IllegalArgumentException("改约提案状态不可拒绝");
        }
        if (isExpired(snapshot, LocalDateTime.now())) {
            rescheduleExpiryService.expireOne(rescheduleId);
            throw new IllegalArgumentException("改约提案已过期");
        }

        Appointment appointmentSnapshot = appointmentRepository.findById(snapshot.getAppointmentId())
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        lockStaff(appointmentSnapshot.getStaffId());
        entityManager.clear();
        Appointment appointment = appointmentRepository.findByIdForUpdate(snapshot.getAppointmentId())
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        AppointmentReschedule reschedule = rescheduleRepository.findByIdForUpdate(rescheduleId)
                .orElseThrow(() -> new IllegalArgumentException("改约提案不存在"));

        if (!RescheduleStatus.PENDING.equals(reschedule.getStatus())) {
            throw new IllegalArgumentException("改约提案状态不可拒绝");
        }
        if (!AppointmentStatus.RESCHEDULING.equals(appointment.getStatus())) {
            throw new IllegalArgumentException("预约当前不在改约中");
        }
        if (isExpired(reschedule, LocalDateTime.now())) {
            throw new IllegalArgumentException("改约提案已过期");
        }
        assertResponder(appointment, reschedule, currentUser);

        reschedule.setStatus(RescheduleStatus.REJECTED);
        reschedule.setRespondedAt(LocalDateTime.now());
        rescheduleRepository.save(reschedule);

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointmentRepository.save(appointment);
        return toResponse(reschedule);
    }

    public void expirePending() {
        List<AppointmentReschedule> expired = rescheduleRepository.findByStatusAndExpireAtBefore(
                RescheduleStatus.PENDING, LocalDateTime.now());
        int count = 0;
        for (AppointmentReschedule reschedule : expired) {
            if (rescheduleExpiryService.expireOne(reschedule.getId())) {
                count++;
            }
        }
        log.info("本次过期改约提案 {} 条", count);
    }

    public RescheduleResponse toResponse(AppointmentReschedule reschedule) {
        String proposerName = sysUserRepository.findById(reschedule.getProposerId())
                .map(user -> user.getRealName() != null ? user.getRealName() : user.getUsername())
                .orElse(null);
        return RescheduleResponse.builder()
                .id(reschedule.getId())
                .appointmentId(reschedule.getAppointmentId())
                .proposerId(reschedule.getProposerId())
                .proposerName(proposerName)
                .proposedStart(reschedule.getProposedStart())
                .status(reschedule.getStatus())
                .expireAt(reschedule.getExpireAt())
                .respondedAt(reschedule.getRespondedAt())
                .reason(reschedule.getReason())
                .createdAt(reschedule.getCreatedAt())
                .build();
    }

    private void assertProposer(Appointment appointment, CurrentUser currentUser) {
        if (currentUser.getUserId().equals(appointment.getCustomerId())) {
            return;
        }
        if (ROLE_STAFF.equals(currentUser.getRoleCode())
                && staffService.getCurrentStaffId().equals(appointment.getStaffId())) {
            return;
        }
        throw new IllegalArgumentException("无权操作");
    }

    private void assertResponder(Appointment appointment,
                                 AppointmentReschedule reschedule,
                                 CurrentUser currentUser) {
        StaffProfile staff = staffProfileRepository.findById(appointment.getStaffId())
                .orElseThrow(() -> new IllegalArgumentException("美甲师不存在"));
        Long customerId = appointment.getCustomerId();
        Long staffUserId = staff.getUserId();
        Long proposerId = reschedule.getProposerId();

        Long expectedResponderId;
        if (proposerId.equals(customerId)) {
            expectedResponderId = staffUserId;
        } else if (proposerId.equals(staffUserId)) {
            expectedResponderId = customerId;
        } else {
            throw new IllegalArgumentException("改约提案数据异常");
        }

        if (!expectedResponderId.equals(currentUser.getUserId())) {
            throw new IllegalArgumentException("无权操作");
        }
    }

    private void ensureNoOccupiedOverlap(Long staffId,
                                         LocalDateTime start,
                                         LocalDateTime end,
                                         Long excludeAppointmentId) {
        LocalDateTime lookbackStart = start.minusHours(CONFLICT_LOOKBACK_HOURS);
        List<Appointment> candidates = appointmentRepository
                .findByStaffIdAndStatusInAndConfirmedStartBetween(
                        staffId, OCCUPYING_STATUSES, lookbackStart, end);
        for (Appointment candidate : candidates) {
            if (candidate.getId().equals(excludeAppointmentId)) {
                continue;
            }
            if (candidate.getConfirmedStart() == null || candidate.getEstDurationMinutes() == null) {
                continue;
            }
            LocalDateTime candidateEnd = candidate.getConfirmedStart()
                    .plusMinutes(candidate.getEstDurationMinutes());
            if (overlaps(start, end, candidate.getConfirmedStart(), candidateEnd)) {
                throw new IllegalArgumentException("新时段已被占用，请选择其他时间");
            }
        }
    }

    private List<TimeSlot> validateNewSlots(Appointment appointment, LocalDateTime newStart) {
        Integer duration = appointment.getEstDurationMinutes();
        validateDuration(duration);
        LocalDateTime newEnd = newStart.plusMinutes(duration);
        List<TimeSlot> slots = findCoveredSlots(appointment.getStaffId(), newStart, newEnd);

        int expectedCount = duration / SLOT_MINUTES;
        if (slots.size() != expectedCount) {
            throw new IllegalArgumentException("新时段已不在可预约范围，请选择其他时间");
        }

        LocalDateTime oldStart = appointment.getConfirmedStart() != null
                ? appointment.getConfirmedStart()
                : appointment.getRequestedStart();
        LocalDateTime oldEnd = oldStart.plusMinutes(duration);

        for (int i = 0; i < expectedCount; i++) {
            TimeSlot slot = slots.get(i);
            LocalDateTime expectedStart = newStart.plusMinutes((long) i * SLOT_MINUTES);
            if (!expectedStart.equals(slot.getSlotStart())
                    || !expectedStart.plusMinutes(SLOT_MINUTES).equals(slot.getSlotEnd())) {
                throw new IllegalArgumentException("新时段已不在可预约范围，请选择其他时间");
            }
            boolean selfOldSlot = STATUS_BOOKED.equals(slot.getStatus())
                    && !slot.getSlotStart().isBefore(oldStart)
                    && slot.getSlotStart().isBefore(oldEnd);
            if (!STATUS_AVAILABLE.equals(slot.getStatus()) && !selfOldSlot) {
                throw new IllegalArgumentException("新时段存在不可用时间槽，请选择其他时间");
            }
        }
        return slots;
    }

    private List<TimeSlot> findCoveredSlots(Long staffId, LocalDateTime start, LocalDateTime end) {
        return timeSlotRepository
                .findByStaffIdAndSlotStartGreaterThanEqualAndSlotStartLessThanOrderBySlotStartAsc(
                        staffId, start, end);
    }

    private void lockStaff(Long staffId) {
        staffProfileRepository.findByIdForUpdate(staffId)
                .orElseThrow(() -> new IllegalArgumentException("美甲师不存在"));
    }

    private void validateDuration(Integer duration) {
        if (duration == null || duration <= 0 || duration % SLOT_MINUTES != 0) {
            throw new IllegalArgumentException("预约时长配置异常");
        }
    }

    private boolean isExpired(AppointmentReschedule reschedule, LocalDateTime now) {
        return reschedule.getExpireAt() != null && reschedule.getExpireAt().isBefore(now);
    }

    private boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd,
                             LocalDateTime bStart, LocalDateTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    private CurrentUser requireCurrentUser() {
        CurrentUser currentUser = CurrentUserHolder.get();
        if (currentUser == null) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }
        return currentUser;
    }
}

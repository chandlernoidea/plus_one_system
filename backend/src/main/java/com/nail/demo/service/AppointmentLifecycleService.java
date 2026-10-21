package com.nail.demo.service;

import java.time.LocalDateTime;
import java.util.List;

import com.nail.demo.common.AppointmentStatus;
import com.nail.demo.common.CurrentUser;
import com.nail.demo.common.CurrentUserHolder;
import com.nail.demo.entity.Appointment;
import com.nail.demo.entity.TimeSlot;
import com.nail.demo.repository.AppointmentRepository;
import com.nail.demo.repository.StaffProfileRepository;
import com.nail.demo.repository.TimeSlotRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AppointmentLifecycleService {

    private static final int SLOT_MINUTES = 15;
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_BOOKED = "BOOKED";
    private static final String STATUS_IN_SERVICE = "IN_SERVICE";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_STAFF = "STAFF";

    private final AppointmentRepository appointmentRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final StaffService staffService;
    private final EntityManager entityManager;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Appointment start(Long id) {
        CurrentUser currentUser = requireCurrentUser();
        requireStaffOrAdmin(currentUser);
        Appointment appointment = lockAppointment(id, currentUser);
        if (!AppointmentStatus.CONFIRMED.equals(appointment.getStatus())) {
            throw new IllegalArgumentException("只有已确认的预约可以开始服务");
        }

        appointment.setActualStart(LocalDateTime.now());
        appointment.setStatus(AppointmentStatus.IN_SERVICE);
        appointmentRepository.save(appointment);

        List<TimeSlot> slots = findCoveredSlots(appointment);
        for (TimeSlot slot : slots) {
            if (STATUS_BOOKED.equals(slot.getStatus())) {
                slot.setStatus(STATUS_IN_SERVICE);
            }
        }
        timeSlotRepository.saveAll(slots);
        return appointment;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Appointment complete(Long id, List<String> finishImageUrls) {
        CurrentUser currentUser = requireCurrentUser();
        requireStaffOrAdmin(currentUser);
        Appointment appointment = lockAppointment(id, currentUser);
        if (!AppointmentStatus.IN_SERVICE.equals(appointment.getStatus())) {
            throw new IllegalArgumentException("只有服务中的预约可以完成");
        }

        List<String> images = cleanImages(finishImageUrls);
        appointment.setActualEnd(LocalDateTime.now());
        appointment.setFinishImageUrls(images.isEmpty() ? null : String.join(",", images));
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appointment);

        List<TimeSlot> slots = findCoveredSlots(appointment);
        for (TimeSlot slot : slots) {
            slot.setStatus(STATUS_COMPLETED);
        }
        timeSlotRepository.saveAll(slots);
        return appointment;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Appointment noShow(Long id) {
        CurrentUser currentUser = requireCurrentUser();
        requireStaffOrAdmin(currentUser);
        Appointment appointment = lockAppointment(id, currentUser);
        if (!AppointmentStatus.CONFIRMED.equals(appointment.getStatus())) {
            throw new IllegalArgumentException("只有已确认的预约可以标记爽约");
        }

        appointment.setStatus(AppointmentStatus.NO_SHOW);
        appointment.setNoShowFlag(1);
        appointmentRepository.save(appointment);

        List<TimeSlot> slots = findCoveredSlots(appointment);
        for (TimeSlot slot : slots) {
            if (STATUS_BOOKED.equals(slot.getStatus())) {
                slot.setStatus(STATUS_AVAILABLE);
            }
        }
        timeSlotRepository.saveAll(slots);
        return appointment;
    }

    private Appointment lockAppointment(Long id, CurrentUser currentUser) {
        Appointment snapshot = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        if (ROLE_STAFF.equals(currentUser.getRoleCode())) {
            Long currentStaffId = staffService.getCurrentStaffId();
            if (!snapshot.getStaffId().equals(currentStaffId)) {
                throw new IllegalArgumentException("无权操作");
            }
        } else if (!ROLE_ADMIN.equals(currentUser.getRoleCode())) {
            throw new IllegalArgumentException("无权操作");
        }

        staffProfileRepository.findByIdForUpdate(snapshot.getStaffId())
                .orElseThrow(() -> new IllegalArgumentException("美甲师不存在"));
        entityManager.clear();
        return appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
    }

    private List<String> cleanImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = images.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        if (cleaned.size() > 6) {
            throw new IllegalArgumentException("完工图最多上传6张");
        }
        return cleaned;
    }

    private List<TimeSlot> findCoveredSlots(Appointment appointment) {
        Integer duration = appointment.getEstDurationMinutes();
        if (duration == null || duration <= 0 || duration % SLOT_MINUTES != 0) {
            throw new IllegalArgumentException("预约时长配置异常");
        }
        LocalDateTime start = appointment.getConfirmedStart() != null
                ? appointment.getConfirmedStart()
                : appointment.getRequestedStart();
        return timeSlotRepository
                .findByStaffIdAndSlotStartGreaterThanEqualAndSlotStartLessThanOrderBySlotStartAsc(
                        appointment.getStaffId(), start, start.plusMinutes(duration));
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

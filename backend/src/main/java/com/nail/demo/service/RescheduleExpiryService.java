package com.nail.demo.service;

import java.time.LocalDateTime;

import com.nail.demo.common.AppointmentStatus;
import com.nail.demo.common.RescheduleStatus;
import com.nail.demo.entity.Appointment;
import com.nail.demo.entity.AppointmentReschedule;
import com.nail.demo.repository.AppointmentRepository;
import com.nail.demo.repository.AppointmentRescheduleRepository;
import com.nail.demo.repository.StaffProfileRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 独立事务处理过期改约，避免接受/拒绝主事务回滚导致状态丢失。
 */
@Service
@RequiredArgsConstructor
public class RescheduleExpiryService {

    private final AppointmentRescheduleRepository rescheduleRepository;
    private final AppointmentRepository appointmentRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean expireOne(Long rescheduleId) {
        AppointmentReschedule snapshot = rescheduleRepository.findById(rescheduleId).orElse(null);
        if (snapshot == null
                || !RescheduleStatus.PENDING.equals(snapshot.getStatus())
                || snapshot.getExpireAt() == null
                || !snapshot.getExpireAt().isBefore(LocalDateTime.now())) {
            return false;
        }

        Appointment appointmentSnapshot = appointmentRepository.findById(snapshot.getAppointmentId())
                .orElse(null);
        if (appointmentSnapshot == null) {
            return false;
        }

        staffProfileRepository.findByIdForUpdate(appointmentSnapshot.getStaffId())
                .orElseThrow(() -> new IllegalArgumentException("美甲师不存在"));
        entityManager.clear();
        Appointment appointment = appointmentRepository.findByIdForUpdate(snapshot.getAppointmentId())
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        AppointmentReschedule reschedule = rescheduleRepository.findByIdForUpdate(rescheduleId)
                .orElseThrow(() -> new IllegalArgumentException("改约提案不存在"));

        LocalDateTime now = LocalDateTime.now();
        if (!RescheduleStatus.PENDING.equals(reschedule.getStatus())
                || reschedule.getExpireAt() == null
                || !reschedule.getExpireAt().isBefore(now)) {
            return false;
        }

        reschedule.setStatus(RescheduleStatus.EXPIRED);
        reschedule.setRespondedAt(now);
        rescheduleRepository.save(reschedule);

        if (AppointmentStatus.RESCHEDULING.equals(appointment.getStatus())) {
            appointment.setStatus(AppointmentStatus.CONFIRMED);
            appointmentRepository.save(appointment);
        }
        return true;
    }
}

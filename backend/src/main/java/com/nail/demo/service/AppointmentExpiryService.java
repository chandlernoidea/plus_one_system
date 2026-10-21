package com.nail.demo.service;

import java.time.LocalDateTime;

import com.nail.demo.common.AppointmentStatus;
import com.nail.demo.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 独立的过期事务，确保“置为 EXPIRED”不会被审批主事务回滚。
 */
@Service
@RequiredArgsConstructor
public class AppointmentExpiryService {

    private final AppointmentRepository appointmentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireOne(Long appointmentId) {
        int updated = appointmentRepository.expireById(
                appointmentId,
                AppointmentStatus.PENDING,
                AppointmentStatus.EXPIRED,
                LocalDateTime.now());
        return updated > 0;
    }
}

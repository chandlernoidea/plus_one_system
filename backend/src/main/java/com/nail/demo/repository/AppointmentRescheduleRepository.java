package com.nail.demo.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.nail.demo.entity.AppointmentReschedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRescheduleRepository extends JpaRepository<AppointmentReschedule, Long> {

    Optional<AppointmentReschedule> findByAppointmentIdAndStatus(Long appointmentId, String status);

    List<AppointmentReschedule> findByStatusAndExpireAtBefore(String status, LocalDateTime time);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AppointmentReschedule r where r.id = :id")
    Optional<AppointmentReschedule> findByIdForUpdate(@Param("id") Long id);
}

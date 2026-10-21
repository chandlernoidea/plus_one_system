package com.nail.demo.repository;

import java.util.List;

import com.nail.demo.entity.AppointmentStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentStatusLogRepository extends JpaRepository<AppointmentStatusLog, Long> {

    List<AppointmentStatusLog> findByAppointmentIdOrderByCreatedAtDesc(Long appointmentId);
}

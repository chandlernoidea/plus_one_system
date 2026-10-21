package com.nail.demo.repository;

import java.util.List;
import java.util.Optional;

import com.nail.demo.entity.AppointmentPrep;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentPrepRepository extends JpaRepository<AppointmentPrep, Long> {

    Optional<AppointmentPrep> findByAppointmentId(Long appointmentId);

    List<AppointmentPrep> findByAppointmentIdIn(List<Long> appointmentIds);
}

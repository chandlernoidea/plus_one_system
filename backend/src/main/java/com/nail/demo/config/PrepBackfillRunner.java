package com.nail.demo.config;

import com.nail.demo.common.AppointmentStatus;
import com.nail.demo.entity.Appointment;
import com.nail.demo.repository.AppointmentRepository;
import com.nail.demo.service.PrepService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 阶段 3 已经确认的预约没有工单，启动时幂等补建，保证阶段 4 页面可用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrepBackfillRunner implements CommandLineRunner {

    private final AppointmentRepository appointmentRepository;
    private final PrepService prepService;

    @Override
    public void run(String... args) {
        List<Appointment> appointments = appointmentRepository.findByStatusIn(List.of(
                AppointmentStatus.CONFIRMED,
                AppointmentStatus.IN_SERVICE,
                AppointmentStatus.RESCHEDULING
        ));
        int created = 0;
        for (Appointment appointment : appointments) {
            if (prepService.findEntityOrNull(appointment.getId()) == null) {
                prepService.createForAppointment(appointment.getId());
                created++;
            }
        }
        log.info("工单补建完成，共补建 {} 条", created);
    }
}

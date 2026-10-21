package com.nail.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppointmentExpireTask {

    private final AppointmentService appointmentService;

    @Scheduled(fixedDelay = 600000)
    public void expirePendingAppointments() {
        appointmentService.expirePending();
    }
}

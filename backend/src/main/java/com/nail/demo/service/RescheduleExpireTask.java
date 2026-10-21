package com.nail.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RescheduleExpireTask {

    private final RescheduleService rescheduleService;

    @Scheduled(fixedDelay = 600000)
    public void expirePendingReschedules() {
        rescheduleService.expirePending();
    }
}

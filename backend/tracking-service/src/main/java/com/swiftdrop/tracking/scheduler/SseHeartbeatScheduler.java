package com.swiftdrop.tracking.scheduler;

import com.swiftdrop.tracking.service.SseEmitterService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * FR-TRK-003: Sends `: heartbeat` comment to all SSE streams every 25 seconds.
 */
@Component
public class SseHeartbeatScheduler {

    private final SseEmitterService sseEmitterService;

    public SseHeartbeatScheduler(SseEmitterService sseEmitterService) {
        this.sseEmitterService = sseEmitterService;
    }

    @Scheduled(fixedRate = 25000)
    public void sendHeartbeats() {
        sseEmitterService.sendHeartbeats();
    }
}


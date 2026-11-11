package com.swiftdrop.parcel.create.scheduler;

import com.swiftdrop.parcel.create.service.IdempotencyService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** FR-PAR-006: expired successful responses are purged hourly; no contact data is logged. */
@Component
public class IdempotencyCleanupScheduler {
    private final IdempotencyService service;

    public IdempotencyCleanupScheduler(IdempotencyService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public void purgeExpired() {
        service.purgeExpired();
    }
}

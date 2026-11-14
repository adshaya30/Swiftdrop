package com.swiftdrop.tracking.audit.scheduler;

import com.swiftdrop.tracking.audit.service.AuditLogRetentionService;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** FR-TRK-005: daily purge of audit entries older than 1 year (one instance, BR-27). */
@Component
public class AuditLogPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditLogPurgeScheduler.class);

    private final AuditLogRetentionService retention;

    public AuditLogPurgeScheduler(AuditLogRetentionService retention) {
        this.retention = retention;
    }

    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Colombo")
    public void purge() {
        int removed = retention.purgeOlderThan(ZonedDateTime.now(ZoneOffset.UTC).minusYears(1).toInstant());
        log.info("Audit log retention purge removed {} entries", removed);
    }
}

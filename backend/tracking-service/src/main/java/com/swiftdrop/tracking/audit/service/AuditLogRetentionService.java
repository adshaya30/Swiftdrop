package com.swiftdrop.tracking.audit.service;

import com.swiftdrop.tracking.audit.repository.AuditLogQueryRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-TRK-005: audit entries are kept 1 year. */
@Service
public class AuditLogRetentionService {

    private final AuditLogQueryRepository repository;

    public AuditLogRetentionService(AuditLogQueryRepository repository) {
        this.repository = repository;
    }

    /** Deletes entries whose {@code occurred_at} is before the cutoff; returns how many were removed. */
    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        return repository.deleteOlderThan(cutoff);
    }
}

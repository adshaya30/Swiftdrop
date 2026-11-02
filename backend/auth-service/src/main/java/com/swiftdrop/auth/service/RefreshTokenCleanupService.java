package com.swiftdrop.auth.service;

import com.swiftdrop.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled cleanup service for purging expired/used refresh tokens.
 * SRS v2.1 – Section 6.5, FR-AUTH-026
 *
 * Retention Rule:
 * Used or expired refresh tokens are retained for 7 days post-expiry to support
 * audit trails and replay-attack detection, and purged thereafter (expires_at <= now - 7 days).
 */
@Service
public class RefreshTokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;
    private final int retentionDays;
    private final int batchSize;
    private final boolean enabled;

    public RefreshTokenCleanupService(
            RefreshTokenRepository refreshTokenRepository,
            Clock clock,
            @Value("${auth.cleanup.refresh-tokens.retention-days:7}") int retentionDays,
            @Value("${auth.cleanup.refresh-tokens.batch-size:1000}") int batchSize,
            @Value("${auth.cleanup.refresh-tokens.enabled:true}") boolean enabled) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
        this.enabled = enabled;
    }

    /**
     * Scheduled entry point running daily at 03:00 UTC (configurable).
     * Bounded execution and safe exception handling prevent scheduler thread death.
     */
    @Scheduled(cron = "${auth.cleanup.refresh-tokens.cron:0 0 3 * * *}")
    public void schedulePurge() {
        if (!enabled) {
            log.debug("Refresh token cleanup job is disabled by configuration");
            return;
        }

        try {
            int totalPurged = purgeExpiredRefreshTokens();
            log.info("Scheduled refresh token cleanup job completed successfully: purged {} records", totalPurged);
        } catch (Exception e) {
            // Log generic message without sensitive data or tokens
            log.error("Scheduled refresh token cleanup job encountered an error: {}", e.getMessage());
        }
    }

    /**
     * Executes the token purge up to the computed retention cutoff (expires_at <= now - retentionDays).
     * Performs bounded batch deletion to prevent lock contention.
     * Idempotent: Subsequent calls with no new eligible tokens return 0.
     *
     * @return total number of purged records
     */
    @Transactional
    public int purgeExpiredRefreshTokens() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);

        int totalDeleted = 0;
        int deletedInBatch;

        // Bounded loop: delete up to 10 batches (10,000 records) per run to keep transactions bounded
        int maxBatches = 10;
        int batchCount = 0;

        do {
            deletedInBatch = refreshTokenRepository.deleteExpiredTokensBefore(cutoff, batchSize);
            totalDeleted += deletedInBatch;
            batchCount++;
        } while (deletedInBatch == batchSize && batchCount < maxBatches);

        if (totalDeleted > 0) {
            log.info("Purged {} expired refresh tokens eligible before retention cutoff {}", totalDeleted, cutoff);
        }

        return totalDeleted;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public boolean isEnabled() {
        return enabled;
    }
}


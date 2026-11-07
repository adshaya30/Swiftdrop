package com.swiftdrop.auth.service;

import com.swiftdrop.auth.google.domain.OAuthLoginStateRepository;
import com.swiftdrop.auth.domain.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled cleanup service for purging expired/used refresh tokens and OAuth login states.
 * SRS v2.1 – Section 6.5, FR-AUTH-026
 *
 * Retention Rule:
 * Used or expired refresh tokens are retained for 7 days post-expiry to support
 * audit trails and replay-attack detection, and purged thereafter (expires_at <= now - 7 days).
 *
 * Expired OAuth login states are purged hourly (expires_at <= now).
 */
@Service
public class RefreshTokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final OAuthLoginStateRepository oAuthLoginStateRepository;
    private final Clock clock;
    private final int retentionDays;
    private final int batchSize;
    private final boolean enabled;

    @Autowired
    public RefreshTokenCleanupService(
            RefreshTokenRepository refreshTokenRepository,
            OAuthLoginStateRepository oAuthLoginStateRepository,
            Clock clock,
            @Value("${auth.cleanup.refresh-tokens.retention-days:7}") int retentionDays,
            @Value("${auth.cleanup.refresh-tokens.batch-size:1000}") int batchSize,
            @Value("${auth.cleanup.refresh-tokens.enabled:true}") boolean enabled) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.oAuthLoginStateRepository = oAuthLoginStateRepository;
        this.clock = clock;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
        this.enabled = enabled;
    }

    public RefreshTokenCleanupService(
            RefreshTokenRepository refreshTokenRepository,
            Clock clock,
            int retentionDays,
            int batchSize,
            boolean enabled) {
        this(refreshTokenRepository, null, clock, retentionDays, batchSize, enabled);
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
     * Hourly scheduled purge for expired transient OAuth 2.0 PKCE login states (FR-AUTH-026).
     */
    @Scheduled(cron = "${auth.cleanup.oauth-states.cron:0 0 * * * *}")
    public void scheduleOAuthStatesPurge() {
        if (!enabled || oAuthLoginStateRepository == null) {
            return;
        }

        try {
            int purged = purgeExpiredOAuthStates();
            if (purged > 0) {
                log.info("Scheduled OAuth login states cleanup completed: purged {} expired states", purged);
            }
        } catch (Exception e) {
            log.error("Scheduled OAuth states cleanup encountered an error: {}", e.getMessage());
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

    /**
     * Purges expired OAuth login states (expires_at <= now).
     *
     * @return total number of purged states
     */
    @Transactional
    public int purgeExpiredOAuthStates() {
        if (oAuthLoginStateRepository == null) {
            return 0;
        }
        Instant now = clock.instant();
        int deleted = oAuthLoginStateRepository.deleteExpiredStatesBefore(now);
        if (deleted > 0) {
            log.info("Purged {} expired OAuth login states eligible before {}", deleted, now);
        }
        return deleted;
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

package com.swiftdrop.auth.login.scheduler;

import com.swiftdrop.auth.domain.RefreshTokenRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled cleanup jobs per FR-AUTH-026 and Master Prompt Prompt 3:
 * - Used or expired refresh tokens purged 7 days after expiry.
 * - Expired oauth_login_states purged hourly via native delete (no entity dependency on #2).
 */
@Component
public class TokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupScheduler.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final EntityManager entityManager;
    private final int retentionDays;
    private final int batchSize;

    public TokenCleanupScheduler(
            RefreshTokenRepository refreshTokenRepository,
            EntityManager entityManager,
            @Value("${auth.cleanup.retention-days:7}") int retentionDays,
            @Value("${auth.cleanup.batch-size:1000}") int batchSize) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.entityManager = entityManager;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
    }

    /**
     * Purges expired refresh tokens 7 days after expiry.
     */
    @Scheduled(cron = "${auth.cleanup.tokens-cron:0 0 2 * * *}")
    @Transactional
    public int purgeExpiredTokens() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = refreshTokenRepository.deleteExpiredTokensBefore(cutoff, batchSize);
        log.info("Purged {} expired refresh tokens older than cutoff {}", deleted, cutoff);
        return deleted;
    }

    /**
     * Purges expired OAuth login states hourly via native SQL query.
     */
    @Scheduled(cron = "${auth.cleanup.oauth-cron:0 0 * * * *}")
    @Transactional
    public int purgeExpiredOAuthLoginStates() {
        Instant now = Instant.now();
        int deleted = entityManager.createNativeQuery(
                "DELETE FROM oauth_login_states WHERE expires_at <= :now")
                .setParameter("now", now)
                .executeUpdate();
        log.info("Hourly purge: deleted {} expired oauth_login_states", deleted);
        return deleted;
    }
}

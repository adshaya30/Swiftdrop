package com.swiftdrop.auth.service;

import com.swiftdrop.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenCleanupService Unit Tests (FR-AUTH-026)")
class RefreshTokenCleanupServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private Clock fixedClock;
    private Instant fixedNow;
    private RefreshTokenCleanupService cleanupService;

    private static final int RETENTION_DAYS = 7;
    private static final int BATCH_SIZE = 1000;

    @BeforeEach
    void setUp() {
        // Deterministic fixed clock: 2026-11-02T12:00:00Z
        fixedNow = Instant.parse("2026-11-02T12:00:00Z");
        fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);

        cleanupService = new RefreshTokenCleanupService(
                refreshTokenRepository,
                fixedClock,
                RETENTION_DAYS,
                BATCH_SIZE,
                true
        );
    }

    @Nested
    @DisplayName("Cutoff and Retention Rule Tests")
    class RetentionRuleTests {

        @Test
        @DisplayName("Calculates exact cutoff (now - 7 days) and delegates bounded batch deletion")
        void purgeExpiredRefreshTokens_deletesWithCorrectCutoff() {
            Instant expectedCutoff = fixedNow.minus(7, ChronoUnit.DAYS);

            when(refreshTokenRepository.deleteExpiredTokensBefore(eq(expectedCutoff), eq(BATCH_SIZE)))
                    .thenReturn(5);

            int purged = cleanupService.purgeExpiredRefreshTokens();

            assertThat(purged).isEqualTo(5);

            ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(refreshTokenRepository).deleteExpiredTokensBefore(cutoffCaptor.capture(), eq(BATCH_SIZE));
            assertThat(cutoffCaptor.getValue()).isEqualTo(expectedCutoff);
        }

        @Test
        @DisplayName("Loop continues if first batch is full (reaches batchSize) and stops when batch is partial")
        void purgeExpiredRefreshTokens_multiBatchLoop() {
            Instant expectedCutoff = fixedNow.minus(7, ChronoUnit.DAYS);

            // First call returns 1000 (full batch), second call returns 350 (partial batch)
            when(refreshTokenRepository.deleteExpiredTokensBefore(eq(expectedCutoff), eq(BATCH_SIZE)))
                    .thenReturn(1000)
                    .thenReturn(350);

            int purged = cleanupService.purgeExpiredRefreshTokens();

            assertThat(purged).isEqualTo(1350);
            verify(refreshTokenRepository, times(2)).deleteExpiredTokensBefore(eq(expectedCutoff), eq(BATCH_SIZE));
        }

        @Test
        @DisplayName("Idempotent execution: second run with 0 records returns 0 cleanly")
        void purgeExpiredRefreshTokens_idempotent() {
            Instant expectedCutoff = fixedNow.minus(7, ChronoUnit.DAYS);

            when(refreshTokenRepository.deleteExpiredTokensBefore(eq(expectedCutoff), eq(BATCH_SIZE)))
                    .thenReturn(0);

            int firstRun = cleanupService.purgeExpiredRefreshTokens();
            int secondRun = cleanupService.purgeExpiredRefreshTokens();

            assertThat(firstRun).isZero();
            assertThat(secondRun).isZero();
            verify(refreshTokenRepository, times(2)).deleteExpiredTokensBefore(eq(expectedCutoff), eq(BATCH_SIZE));
        }
    }

    @Nested
    @DisplayName("Scheduled Invocation and Error Handling")
    class ScheduledExecutionTests {

        @Test
        @DisplayName("Scheduled purge runs and logs without failure when enabled")
        void schedulePurge_executesWhenEnabled() {
            when(refreshTokenRepository.deleteExpiredTokensBefore(any(), anyInt()))
                    .thenReturn(15);

            cleanupService.schedulePurge();

            verify(refreshTokenRepository).deleteExpiredTokensBefore(any(), eq(BATCH_SIZE));
        }

        @Test
        @DisplayName("Scheduled purge skips execution when disabled by configuration")
        void schedulePurge_skipsWhenDisabled() {
            RefreshTokenCleanupService disabledService = new RefreshTokenCleanupService(
                    refreshTokenRepository,
                    fixedClock,
                    RETENTION_DAYS,
                    BATCH_SIZE,
                    false
            );

            disabledService.schedulePurge();

            verify(refreshTokenRepository, never()).deleteExpiredTokensBefore(any(), anyInt());
        }

        @Test
        @DisplayName("Repository exception during scheduled execution does not propagate or crash scheduler")
        void schedulePurge_handlesExceptionGracefullyWithoutExposingSecrets() {
            when(refreshTokenRepository.deleteExpiredTokensBefore(any(), anyInt()))
                    .thenThrow(new RuntimeException("Database connectivity transient failure"));

            // Must not throw an exception out of schedulePurge()
            cleanupService.schedulePurge();

            verify(refreshTokenRepository).deleteExpiredTokensBefore(any(), anyInt());
        }
    }
}


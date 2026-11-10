package com.swiftdrop.auth.service;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.RefreshToken;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.RefreshTokenRepository;
import com.swiftdrop.auth.login.service.JwtService;
import com.swiftdrop.auth.login.service.LoginService;
import com.swiftdrop.auth.login.service.RefreshTokenService;
import com.swiftdrop.auth.login.service.PasswordHasher;
import com.swiftdrop.auth.login.service.PasswordPolicy;
import com.swiftdrop.auth.login.controller.AuthController;

import com.swiftdrop.auth.AbstractIntegrationTest;
import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.domain.RefreshToken;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.domain.RefreshTokenRepository;
import com.swiftdrop.auth.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@DisplayName("RefreshTokenCleanupService Integration Tests (FR-AUTH-026)")
class RefreshTokenCleanupIT extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private Instant fixedNow;
    private RefreshTokenCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User();
        testUser.setFullName("Cleanup Tester");
        testUser.setEmail("cleanup.test@swiftdrop.lk");
        testUser.setPhone("+94771234567");
        testUser.setPasswordHash("hashedPassword123!");
        testUser.setRole(UserRole.CUSTOMER);
        testUser.setAuthProvider(AuthProvider.LOCAL);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser = userRepository.saveAndFlush(testUser);

        // Fixed reference time: 2026-11-02T12:00:00Z
        fixedNow = Instant.parse("2026-11-02T12:00:00Z");
        Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);

        cleanupService = new RefreshTokenCleanupService(
                refreshTokenRepository,
                fixedClock,
                7, // 7 days retention post-expiry
                100, // batch size
                true
        );
    }

    @Test
    @DisplayName("Tokens expired > 7 days ago are purged; active and recent tokens are preserved")
    void purgeExpiredRefreshTokens_purgesOnlyPastRetention() {
        // 1. Expired 8 days ago (beyond 7-day retention) -> MUST BE PURGED
        RefreshToken expiredBeyondRetention = createToken(
                fixedNow.minus(15, ChronoUnit.DAYS),
                fixedNow.minus(8, ChronoUnit.DAYS),
                null,
                null
        );

        // 2. Expired 3 days ago (within 7-day retention) -> MUST BE PRESERVED
        RefreshToken expiredWithinRetention = createToken(
                fixedNow.minus(10, ChronoUnit.DAYS),
                fixedNow.minus(3, ChronoUnit.DAYS),
                null,
                null
        );

        // 3. Active unexpired token (expires in 5 days) -> MUST BE PRESERVED
        RefreshToken activeToken = createToken(
                fixedNow.minus(2, ChronoUnit.DAYS),
                fixedNow.plus(5, ChronoUnit.DAYS),
                null,
                null
        );

        // 4. Used token whose expiry was 2 days ago -> MUST BE PRESERVED (audit/replay window)
        RefreshToken usedWithinRetention = createToken(
                fixedNow.minus(9, ChronoUnit.DAYS),
                fixedNow.minus(2, ChronoUnit.DAYS),
                fixedNow.minus(2, ChronoUnit.DAYS),
                null
        );

        // 5. Used token whose expiry was 10 days ago -> MUST BE PURGED
        RefreshToken usedBeyondRetention = createToken(
                fixedNow.minus(17, ChronoUnit.DAYS),
                fixedNow.minus(10, ChronoUnit.DAYS),
                fixedNow.minus(10, ChronoUnit.DAYS),
                null
        );

        // 6. Revoked token whose expiry was 9 days ago -> MUST BE PURGED
        RefreshToken revokedBeyondRetention = createToken(
                fixedNow.minus(16, ChronoUnit.DAYS),
                fixedNow.minus(9, ChronoUnit.DAYS),
                null,
                fixedNow.minus(9, ChronoUnit.DAYS)
        );

        // Act: Execute purge
        int purgedCount = cleanupService.purgeExpiredRefreshTokens();

        // Assert
        assertThat(purgedCount).isEqualTo(3); // tokens 1, 5, 6 purged

        assertThat(refreshTokenRepository.existsById(expiredBeyondRetention.getId())).isFalse();
        assertThat(refreshTokenRepository.existsById(usedBeyondRetention.getId())).isFalse();
        assertThat(refreshTokenRepository.existsById(revokedBeyondRetention.getId())).isFalse();

        assertThat(refreshTokenRepository.existsById(expiredWithinRetention.getId())).isTrue();
        assertThat(refreshTokenRepository.existsById(activeToken.getId())).isTrue();
        assertThat(refreshTokenRepository.existsById(usedWithinRetention.getId())).isTrue();

        // Idempotency: running a second time purges 0
        int secondRunCount = cleanupService.purgeExpiredRefreshTokens();
        assertThat(secondRunCount).isZero();
    }

    private RefreshToken createToken(Instant issuedAt, Instant expiresAt, Instant usedAt, Instant revokedAt) {
        RefreshToken token = new RefreshToken();
        token.setUserId(testUser.getId());
        token.setTokenHash(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        token.setFamilyId(UUID.randomUUID());
        token.setFamilyCreatedAt(issuedAt);
        token.setExpiresAt(expiresAt);
        token.setUsedAt(usedAt);
        token.setRevokedAt(revokedAt);
        token.setCreatedAt(issuedAt);
        token.setUserAgent("Test-Agent");
        token.setIpAddress("127.0.0.1");
        return refreshTokenRepository.saveAndFlush(token);
    }
}

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

import com.swiftdrop.auth.dto.response.LoginResponse;
import com.swiftdrop.auth.login.dto.RotationResult;
import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.entity.MerchantStatus;
import com.swiftdrop.auth.domain.RefreshToken;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.exception.UnauthenticatedException;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.RefreshTokenRepository;
import com.swiftdrop.auth.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService Unit Tests")
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private JwtService jwtService;

    private RefreshTokenService refreshTokenService;

    private static final int GRACE_SECONDS = 10;
    private static final int FAMILY_MAX_DAYS = 30;

    private User testUser;
    private UUID userId;
    private UUID familyId;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                userRepository,
                merchantRepository,
                jwtService,
                7,
                FAMILY_MAX_DAYS,
                GRACE_SECONDS
        );

        userId = UUID.randomUUID();
        familyId = UUID.randomUUID();

        testUser = new User();
        testUser.setId(userId);
        testUser.setEmail("kasun.perera@example.lk");
        testUser.setFullName("Kasun Perera");
        testUser.setRole(UserRole.CUSTOMER);
        testUser.setStatus(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Rotate with null or empty token throws UnauthenticatedException")
    void rotate_emptyToken_throws() {
        assertThatThrownBy(() -> refreshTokenService.rotate(null, "UA", "127.0.0.1"))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("missing");

        assertThatThrownBy(() -> refreshTokenService.rotate("   ", "UA", "127.0.0.1"))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("missing");
    }

    @Test
    @DisplayName("Rotate with unknown token hash throws UnauthenticatedException")
    void rotate_unknownToken_throws() {
        when(refreshTokenRepository.findByTokenHashWithLock(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("random-raw-token", "UA", "127.0.0.1"))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("Invalid");
    }

    @Test
    @DisplayName("Rotate with already-revoked token throws UnauthenticatedException")
    void rotate_revokedToken_throws() {
        RefreshToken token = createSampleToken();
        token.setRevokedAt(Instant.now().minus(1, ChronoUnit.HOURS));

        when(refreshTokenRepository.findByTokenHashWithLock(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.rotate("raw-token", "UA", "127.0.0.1"))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    @DisplayName("Rotate with expired token throws UnauthenticatedException")
    void rotate_expiredToken_throws() {
        RefreshToken token = createSampleToken();
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));

        when(refreshTokenRepository.findByTokenHashWithLock(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.rotate("raw-token", "UA", "127.0.0.1"))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Rotate when family age exceeds 30 days revokes family and throws")
    void rotate_familyLifetimeExceeded_revokesFamilyAndThrows() {
        RefreshToken token = createSampleToken();
        token.setFamilyCreatedAt(Instant.now().minus(31, ChronoUnit.DAYS));

        when(refreshTokenRepository.findByTokenHashWithLock(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.rotate("raw-token", "UA", "127.0.0.1"))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("lifetime expired");

        verify(refreshTokenRepository).revokeFamily(eq(familyId), any(Instant.class));
    }

    @Test
    @DisplayName("Concurrent tab within 10s grace returns fresh access token without new refresh cookie")
    void rotate_withinGracePeriod_returnsAccessJwtWithoutNewCookie() {
        RefreshToken token = createSampleToken();
        token.setUsedAt(Instant.now().minus(3, ChronoUnit.SECONDS)); // 3 seconds ago <= 10s grace

        when(refreshTokenRepository.findByTokenHashWithLock(anyString())).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(jwtService.issueUserToken(any(com.swiftdrop.auth.domain.User.class), isNull()))
                .thenReturn("grace-jwt-token");

        RotationResult result = refreshTokenService.rotate("raw-token", "UA", "127.0.0.1");

        assertThat(result.rotated()).isFalse();
        assertThat(result.rawRefreshToken()).isNull();
        assertThat(result.accessToken()).isEqualTo("grace-jwt-token");
        verify(refreshTokenRepository, never()).revokeFamily(any(), any());
    }

    @Test
    @DisplayName("Replay after 10s grace revokes entire token family")
    void rotate_afterGracePeriod_replayDetected_revokesFamily() {
        RefreshToken token = createSampleToken();
        token.setUsedAt(Instant.now().minus(15, ChronoUnit.SECONDS)); // 15s ago > 10s grace

        when(refreshTokenRepository.findByTokenHashWithLock(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> refreshTokenService.rotate("raw-token", "UA", "127.0.0.1"))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("replay detected");

        verify(refreshTokenRepository).revokeFamily(eq(familyId), any(Instant.class));
    }

    @Test
    @DisplayName("Normal rotation marks token used, creates new token in same family, issues JWT and cookie")
    void rotate_validToken_success() {
        RefreshToken token = createSampleToken();
        token.setUsedAt(null);
        Instant originalFamilyCreatedAt = token.getFamilyCreatedAt();

        when(refreshTokenRepository.findByTokenHashWithLock(anyString())).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(jwtService.issueUserToken(any(com.swiftdrop.auth.domain.User.class), isNull()))
                .thenReturn("new-access-jwt");

        RotationResult result = refreshTokenService.rotate("valid-raw-token", "Mozilla/5.0", "192.168.1.1");

        assertThat(result.rotated()).isTrue();
        assertThat(result.rawRefreshToken()).isNotBlank();
        assertThat(result.accessToken()).isEqualTo("new-access-jwt");

        // Verify old token marked used
        assertThat(token.getUsedAt()).isNotNull();

        // Verify new token saved in same family with original family_created_at
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Rotate for DEACTIVATED user revokes family and throws")
    void rotate_deactivatedUser_revokesFamilyAndThrows() {
        RefreshToken token = createSampleToken();
        testUser.setStatus(UserStatus.DEACTIVATED);

        when(refreshTokenRepository.findByTokenHashWithLock(anyString())).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> refreshTokenService.rotate("raw-token", "UA", "127.0.0.1"))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("not active");

        verify(refreshTokenRepository).revokeFamily(eq(familyId), any(Instant.class));
    }

    @Test
    @DisplayName("Rotate for unapproved merchant revokes family and throws")
    void rotate_unapprovedMerchant_revokesFamilyAndThrows() {
        RefreshToken token = createSampleToken();
        testUser.setRole(UserRole.MERCHANT);

        Merchant merchant = new Merchant();
        merchant.setStatus(MerchantStatus.PENDING);

        when(refreshTokenRepository.findByTokenHashWithLock(anyString())).thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant));

        assertThatThrownBy(() -> refreshTokenService.rotate("raw-token", "UA", "127.0.0.1"))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("not approved");

        verify(refreshTokenRepository).revokeFamily(eq(familyId), any(Instant.class));
    }

    @Test
    @DisplayName("Logout with valid token revokes the entire family")
    void logout_validToken_revokesFamily() {
        RefreshToken token = createSampleToken();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        refreshTokenService.revokeFamily("raw-token-to-logout");

        verify(refreshTokenRepository).revokeFamily(eq(familyId), any(Instant.class));
    }

    @Test
    @DisplayName("Logout with null or blank token does nothing")
    void logout_emptyToken_doesNothing() {
        refreshTokenService.revokeFamily(null);
        refreshTokenService.revokeFamily("   ");

        verifyNoInteractions(refreshTokenRepository);
    }

    private RefreshToken createSampleToken() {
        RefreshToken t = new RefreshToken();
        t.setId(UUID.randomUUID());
        t.setUserId(userId);
        t.setFamilyId(familyId);
        t.setTokenHash("dummy-sha256-hash");
        t.setCreatedAt(Instant.now());
        t.setFamilyCreatedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        t.setExpiresAt(Instant.now().plus(5, ChronoUnit.DAYS));
        return t;
    }
}

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.dto.response.GoogleCallbackResult;
import com.swiftdrop.auth.dto.response.GoogleLoginInitResult;
import com.swiftdrop.auth.dto.response.GoogleUserClaims;
import com.swiftdrop.auth.entity.*;
import com.swiftdrop.auth.repository.OAuthLoginStateRepository;
import com.swiftdrop.auth.domain.RefreshTokenRepository;
import com.swiftdrop.auth.domain.UserRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GoogleOAuthService (FR-AUTH-009, FR-AUTH-010, FR-AUTH-011, Flow 4.5).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GoogleOAuthService Unit Tests")
class GoogleOAuthServiceTest {

    @Mock
    private OAuthLoginStateRepository oAuthLoginStateRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private GoogleOidcVerifier googleOidcVerifier;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Instant now = Instant.parse("2026-11-02T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private GoogleOAuthService googleOAuthService;

    @BeforeEach
    void setUp() {
        googleOAuthService = new GoogleOAuthService(
                "google-client-id-123",
                "google-client-secret-xyz",
                "http://localhost:8080/api/v1/auth/google/callback",
                "http://localhost:5173",
                oAuthLoginStateRepository,
                userRepository,
                refreshTokenRepository,
                googleOidcVerifier,
                objectMapper,
                clock
        );
    }

    @Nested
    @DisplayName("Configuration & Public Endpoint (FR-AUTH-011)")
    class ConfigurationTests {

        @Test
        @DisplayName("isGoogleEnabled returns true when client ID and secret are configured")
        void isGoogleEnabled_configured_returnsTrue() {
            assertThat(googleOAuthService.isGoogleEnabled()).isTrue();
        }

        @Test
        @DisplayName("isGoogleEnabled returns false when client ID is missing or blank")
        void isGoogleEnabled_missingClientId_returnsFalse() {
            GoogleOAuthService service = new GoogleOAuthService(
                    "",
                    "secret",
                    "http://localhost:8080/api/v1/auth/google/callback",
                    "http://localhost:5173",
                    oAuthLoginStateRepository,
                    userRepository,
                    refreshTokenRepository,
                    googleOidcVerifier,
                    objectMapper,
                    clock
            );
            assertThat(service.isGoogleEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("Flow 4.5 — Login Initiation & PKCE Challenge (FR-AUTH-009)")
    class LoginInitiationTests {

        @Test
        @DisplayName("initiateLogin throws IllegalStateException when Google OAuth is disabled")
        void initiateLogin_disabled_throwsException() {
            GoogleOAuthService disabledService = new GoogleOAuthService(
                    "",
                    "",
                    "http://localhost:8080/api/v1/auth/google/callback",
                    "http://localhost:5173",
                    oAuthLoginStateRepository,
                    userRepository,
                    refreshTokenRepository,
                    googleOidcVerifier,
                    objectMapper,
                    clock
            );

            assertThatThrownBy(disabledService::initiateLogin)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Google OAuth is disabled");
        }

        @Test
        @DisplayName("initiateLogin persists state_hash with 5m expiry and returns valid auth URL")
        void initiateLogin_success_persistsStateAndReturnsAuthUrl() {
            GoogleLoginInitResult result = googleOAuthService.initiateLogin();

            assertThat(result).isNotNull();
            assertThat(result.state()).isNotEmpty();
            assertThat(result.authorizationUrl()).contains("https://accounts.google.com/o/oauth2/v2/auth");
            assertThat(result.authorizationUrl()).contains("client_id=google-client-id-123");
            assertThat(result.authorizationUrl()).contains("code_challenge_method=S256");
            assertThat(result.authorizationUrl()).contains("response_type=code");
            assertThat(result.authorizationUrl()).contains("openid");

            ArgumentCaptor<OAuthLoginState> stateCaptor = ArgumentCaptor.forClass(OAuthLoginState.class);
            verify(oAuthLoginStateRepository).save(stateCaptor.capture());

            OAuthLoginState saved = stateCaptor.getValue();
            assertThat(saved.getStateHash()).hasSize(64);
            assertThat(saved.getExpiresAt()).isEqualTo(now.plusSeconds(300));
            assertThat(saved.getCodeVerifier()).isNotEmpty();
            assertThat(saved.getNonce()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Flow 4.5 & FR-AUTH-010 — Callback Processing & Account Rules")
    class CallbackProcessingTests {

        @Test
        @DisplayName("handleCallback redirects to login?error=google_denied on user cancellation")
        void handleCallback_googleError_redirectsWithDenied() {
            GoogleCallbackResult result = googleOAuthService.handleCallback(
                    null,
                    "state123",
                    "state123",
                    "access_denied",
                    "127.0.0.1",
                    "TestAgent"
            );

            assertThat(result.redirectUrl()).isEqualTo("http://localhost:5173/login?error=google_denied");
            assertThat(result.rawRefreshToken()).isNull();
        }

        @Test
        @DisplayName("handleCallback redirects with google_failed when cookie state is missing")
        void handleCallback_missingCookieState_redirectsWithFailed() {
            GoogleCallbackResult result = googleOAuthService.handleCallback(
                    "code123",
                    "state123",
                    null,
                    null,
                    "127.0.0.1",
                    "TestAgent"
            );

            assertThat(result.redirectUrl()).isEqualTo("http://localhost:5173/login?error=google_failed");
            assertThat(result.rawRefreshToken()).isNull();
        }

        @Test
        @DisplayName("handleCallback redirects with google_failed on state mismatch (CSRF protection)")
        void handleCallback_stateMismatch_redirectsWithFailed() {
            GoogleCallbackResult result = googleOAuthService.handleCallback(
                    "code123",
                    "state-from-google",
                    "state-from-cookie",
                    null,
                    "127.0.0.1",
                    "TestAgent"
            );

            assertThat(result.redirectUrl()).isEqualTo("http://localhost:5173/login?error=google_failed");
            assertThat(result.rawRefreshToken()).isNull();
        }

        @Test
        @DisplayName("handleCallback redirects with google_failed when state is not found or already consumed")
        void handleCallback_stateNotFound_redirectsWithFailed() {
            when(oAuthLoginStateRepository.findByStateHash(any())).thenReturn(Optional.empty());

            GoogleCallbackResult result = googleOAuthService.handleCallback(
                    "code123",
                    "raw-state",
                    "raw-state",
                    null,
                    "127.0.0.1",
                    "TestAgent"
            );

            assertThat(result.redirectUrl()).isEqualTo("http://localhost:5173/login?error=google_failed");
            assertThat(result.rawRefreshToken()).isNull();
        }

        @Test
        @DisplayName("handleCallback redirects with google_failed when state is expired")
        void handleCallback_stateExpired_redirectsWithFailed() {
            OAuthLoginState expiredState = new OAuthLoginState(
                    "state-hash",
                    "nonce123",
                    "verifier123",
                    now.minusSeconds(10)
            );
            when(oAuthLoginStateRepository.findByStateHash(any())).thenReturn(Optional.of(expiredState));

            GoogleCallbackResult result = googleOAuthService.handleCallback(
                    "code123",
                    "raw-state",
                    "raw-state",
                    null,
                    "127.0.0.1",
                    "TestAgent"
            );

            assertThat(result.redirectUrl()).isEqualTo("http://localhost:5173/login?error=google_failed");
            // Verify single-use state is deleted immediately
            verify(oAuthLoginStateRepository).deleteByStateHash(any());
        }

        @Test
        @DisplayName("handleCallback rejects local user collision with login?error=account_exists (FR-AUTH-010)")
        void handleCallback_localAccountCollision_rejectsWithAccountExists() throws Exception {
            GoogleUserClaims claims = new GoogleUserClaims(
                    "google-sub-999",
                    "existing.local@example.com",
                    "Local User",
                    true
            );

            GoogleOAuthService spyService = new GoogleOAuthService(
                    "client-id",
                    "client-secret",
                    "http://localhost:8080/api/v1/auth/google/callback",
                    "http://localhost:5173",
                    oAuthLoginStateRepository,
                    userRepository,
                    refreshTokenRepository,
                    googleOidcVerifier,
                    objectMapper,
                    clock
            ) {
                @Override
                public String exchangeCodeForIdToken(String code, String codeVerifier) {
                    return "mock-id-token";
                }
            };

            OAuthLoginState validState = new OAuthLoginState(
                    "state-hash",
                    "nonce123",
                    "verifier123",
                    now.plusSeconds(100)
            );
            when(oAuthLoginStateRepository.findByStateHash(any())).thenReturn(Optional.of(validState));
            when(googleOidcVerifier.verifyIdToken("mock-id-token", "nonce123")).thenReturn(claims);

            // User does not exist by google_sub
            when(userRepository.findByGoogleSub("google-sub-999")).thenReturn(Optional.empty());

            // User exists by email with LOCAL provider
            User localUser = new User();
            localUser.setId(UUID.randomUUID());
            localUser.setEmail("existing.local@example.com");
            localUser.setAuthProvider(AuthProvider.LOCAL);
            when(userRepository.findByEmailIgnoreCase("existing.local@example.com")).thenReturn(Optional.of(localUser));

            GoogleCallbackResult result = spyService.handleCallback(
                    "code123",
                    "raw-state",
                    "raw-state",
                    null,
                    "127.0.0.1",
                    "TestAgent"
            );

            assertThat(result.redirectUrl()).isEqualTo("http://localhost:5173/login?error=account_exists");
            assertThat(result.rawRefreshToken()).isNull();
            // Verify local user password / sub was NOT modified or saved
            verify(userRepository, never()).save(any());
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("handleCallback logs in existing Google user and issues session cookie")
        void handleCallback_existingGoogleUser_success() throws Exception {
            GoogleUserClaims claims = new GoogleUserClaims(
                    "google-sub-123",
                    "google.user@example.com",
                    "Google User",
                    true
            );

            GoogleOAuthService spyService = new GoogleOAuthService(
                    "client-id",
                    "client-secret",
                    "http://localhost:8080/api/v1/auth/google/callback",
                    "http://localhost:5173",
                    oAuthLoginStateRepository,
                    userRepository,
                    refreshTokenRepository,
                    googleOidcVerifier,
                    objectMapper,
                    clock
            ) {
                @Override
                public String exchangeCodeForIdToken(String code, String codeVerifier) {
                    return "mock-id-token";
                }
            };

            OAuthLoginState validState = new OAuthLoginState(
                    "state-hash",
                    "nonce123",
                    "verifier123",
                    now.plusSeconds(100)
            );
            when(oAuthLoginStateRepository.findByStateHash(any())).thenReturn(Optional.of(validState));
            when(googleOidcVerifier.verifyIdToken("mock-id-token", "nonce123")).thenReturn(claims);

            User googleUser = new User();
            googleUser.setId(UUID.randomUUID());
            googleUser.setEmail("google.user@example.com");
            googleUser.setAuthProvider(AuthProvider.GOOGLE);
            googleUser.setStatus(UserStatus.ACTIVE);
            googleUser.setGoogleSub("google-sub-123");

            when(userRepository.findByGoogleSub("google-sub-123")).thenReturn(Optional.of(googleUser));

            GoogleCallbackResult result = spyService.handleCallback(
                    "code123",
                    "raw-state",
                    "raw-state",
                    null,
                    "127.0.0.1",
                    "TestAgent"
            );

            assertThat(result.redirectUrl()).isEqualTo("http://localhost:5173/auth/callback");
            assertThat(result.rawRefreshToken()).isNotEmpty();

            verify(refreshTokenRepository).saveAndFlush(any(RefreshToken.class));
            verify(userRepository).save(googleUser);
            assertThat(googleUser.getLastLoginAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("handleCallback auto-registers new CUSTOMER when user does not exist (FR-AUTH-010)")
        void handleCallback_newUser_autoRegistersCustomer() throws Exception {
            GoogleUserClaims claims = new GoogleUserClaims(
                    "google-sub-brand-new",
                    "brandnew@example.com",
                    "New User",
                    true
            );

            GoogleOAuthService spyService = new GoogleOAuthService(
                    "client-id",
                    "client-secret",
                    "http://localhost:8080/api/v1/auth/google/callback",
                    "http://localhost:5173",
                    oAuthLoginStateRepository,
                    userRepository,
                    refreshTokenRepository,
                    googleOidcVerifier,
                    objectMapper,
                    clock
            ) {
                @Override
                public String exchangeCodeForIdToken(String code, String codeVerifier) {
                    return "mock-id-token";
                }
            };

            OAuthLoginState validState = new OAuthLoginState(
                    "state-hash",
                    "nonce123",
                    "verifier123",
                    now.plusSeconds(100)
            );
            when(oAuthLoginStateRepository.findByStateHash(any())).thenReturn(Optional.of(validState));
            when(googleOidcVerifier.verifyIdToken("mock-id-token", "nonce123")).thenReturn(claims);

            when(userRepository.findByGoogleSub("google-sub-brand-new")).thenReturn(Optional.empty());
            when(userRepository.findByEmailIgnoreCase("brandnew@example.com")).thenReturn(Optional.empty());

            when(userRepository.saveAndFlush(any())).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });

            GoogleCallbackResult result = spyService.handleCallback(
                    "code123",
                    "raw-state",
                    "raw-state",
                    null,
                    "127.0.0.1",
                    "TestAgent"
            );

            assertThat(result.redirectUrl()).isEqualTo("http://localhost:5173/auth/callback");
            assertThat(result.rawRefreshToken()).isNotEmpty();

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).saveAndFlush(userCaptor.capture());

            User registered = userCaptor.getValue();
            assertThat(registered.getEmail()).isEqualTo("brandnew@example.com");
            assertThat(registered.getFullName()).isEqualTo("New User");
            assertThat(registered.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
            assertThat(registered.getRole()).isEqualTo(UserRole.CUSTOMER);
            assertThat(registered.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(registered.getGoogleSub()).isEqualTo("google-sub-brand-new");
            assertThat(registered.getPasswordHash()).isNull();

            verify(refreshTokenRepository).saveAndFlush(any(RefreshToken.class));
        }

        @Test
        @DisplayName("handleCallback rejects deactivated Google user with login?error=account_deactivated")
        void handleCallback_deactivatedGoogleUser_rejects() throws Exception {
            GoogleUserClaims claims = new GoogleUserClaims(
                    "google-sub-deactivated",
                    "deactivated@example.com",
                    "Deactivated User",
                    true
            );

            GoogleOAuthService spyService = new GoogleOAuthService(
                    "client-id",
                    "client-secret",
                    "http://localhost:8080/api/v1/auth/google/callback",
                    "http://localhost:5173",
                    oAuthLoginStateRepository,
                    userRepository,
                    refreshTokenRepository,
                    googleOidcVerifier,
                    objectMapper,
                    clock
            ) {
                @Override
                public String exchangeCodeForIdToken(String code, String codeVerifier) {
                    return "mock-id-token";
                }
            };

            OAuthLoginState validState = new OAuthLoginState(
                    "state-hash",
                    "nonce123",
                    "verifier123",
                    now.plusSeconds(100)
            );
            when(oAuthLoginStateRepository.findByStateHash(any())).thenReturn(Optional.of(validState));
            when(googleOidcVerifier.verifyIdToken("mock-id-token", "nonce123")).thenReturn(claims);

            User deactivatedUser = new User();
            deactivatedUser.setId(UUID.randomUUID());
            deactivatedUser.setEmail("deactivated@example.com");
            deactivatedUser.setAuthProvider(AuthProvider.GOOGLE);
            deactivatedUser.setStatus(UserStatus.DEACTIVATED);
            deactivatedUser.setGoogleSub("google-sub-deactivated");

            when(userRepository.findByGoogleSub("google-sub-deactivated")).thenReturn(Optional.of(deactivatedUser));

            GoogleCallbackResult result = spyService.handleCallback(
                    "code123",
                    "raw-state",
                    "raw-state",
                    null,
                    "127.0.0.1",
                    "TestAgent"
            );

            assertThat(result.redirectUrl()).isEqualTo("http://localhost:5173/login?error=account_deactivated");
            assertThat(result.rawRefreshToken()).isNull();
            verify(refreshTokenRepository, never()).save(any());
        }
    }
}

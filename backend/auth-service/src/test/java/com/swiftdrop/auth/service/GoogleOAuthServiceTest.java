package com.swiftdrop.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.google.domain.OAuthLoginState;
import com.swiftdrop.auth.google.domain.OAuthLoginStateRepository;
import com.swiftdrop.auth.google.dto.GoogleLoginInitResult;
import com.swiftdrop.auth.google.dto.GoogleUserClaims;
import com.swiftdrop.auth.google.service.GoogleOAuthService;
import com.swiftdrop.auth.google.service.GoogleOidcVerifier;
import com.swiftdrop.auth.login.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.web.client.RestClient;

import java.time.Instant;
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
    private RefreshTokenService refreshTokenService;

    @Mock
    private GoogleOidcVerifier googleOidcVerifier;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GoogleOAuthService googleOAuthService;

    @BeforeEach
    void setUp() {
        googleOAuthService = new GoogleOAuthService(
                "google-client-id-123",
                "google-client-secret-xyz",
                "http://localhost:8080/api/v1/auth/google/callback",
                "http://localhost:5173",
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.com/token",
                false,
                oAuthLoginStateRepository,
                userRepository,
                refreshTokenService,
                googleOidcVerifier,
                RestClient.builder(),
                objectMapper
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
                    "https://accounts.google.com/o/oauth2/v2/auth",
                    "https://oauth2.googleapis.com/token",
                    false,
                    oAuthLoginStateRepository,
                    userRepository,
                    refreshTokenService,
                    googleOidcVerifier,
                    RestClient.builder(),
                    objectMapper
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
                    "https://accounts.google.com/o/oauth2/v2/auth",
                    "https://oauth2.googleapis.com/token",
                    false,
                    oAuthLoginStateRepository,
                    userRepository,
                    refreshTokenService,
                    googleOidcVerifier,
                    RestClient.builder(),
                    objectMapper
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
            assertThat(result.rawState()).isNotEmpty();
            assertThat(result.authorizationUrl()).contains("https://accounts.google.com/o/oauth2/v2/auth");
            assertThat(result.authorizationUrl()).contains("client_id=google-client-id-123");
            assertThat(result.authorizationUrl()).contains("code_challenge_method=S256");
            assertThat(result.authorizationUrl()).contains("response_type=code");
            assertThat(result.authorizationUrl()).contains("openid");

            ArgumentCaptor<OAuthLoginState> stateCaptor = ArgumentCaptor.forClass(OAuthLoginState.class);
            verify(oAuthLoginStateRepository).save(stateCaptor.capture());

            OAuthLoginState saved = stateCaptor.getValue();
            assertThat(saved.getStateHash()).hasSize(64);
            assertThat(saved.getExpiresAt()).isAfter(Instant.now());
            assertThat(saved.getCodeVerifier()).isNotEmpty();
            assertThat(saved.getNonce()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Flow 4.5 & FR-AUTH-010 — Callback Processing & Account Rules")
    class CallbackProcessingTests {

        @Test
        @DisplayName("handleCallback redirects with google_failed when cookie state is missing")
        void handleCallback_missingCookieState_redirectsWithFailed() {
            GoogleOAuthService.CallbackOutcome outcome = googleOAuthService.handleCallback(
                    "code123",
                    "state123",
                    null
            );

            assertThat(outcome.redirectUrl()).isEqualTo("http://localhost:5173/login?error=google_failed");
            assertThat(outcome.refreshCookie()).isNull();
        }

        @Test
        @DisplayName("handleCallback redirects with google_failed on state mismatch (CSRF protection)")
        void handleCallback_stateMismatch_redirectsWithFailed() {
            GoogleOAuthService.CallbackOutcome outcome = googleOAuthService.handleCallback(
                    "code123",
                    "state-from-google",
                    "state-from-cookie"
            );

            assertThat(outcome.redirectUrl()).isEqualTo("http://localhost:5173/login?error=google_failed");
            assertThat(outcome.refreshCookie()).isNull();
        }

        @Test
        @DisplayName("handleCallback redirects with google_failed when state is not found")
        void handleCallback_stateNotFound_redirectsWithFailed() {
            when(oAuthLoginStateRepository.findByStateHash(any())).thenReturn(Optional.empty());

            GoogleOAuthService.CallbackOutcome outcome = googleOAuthService.handleCallback(
                    "code123",
                    "raw-state",
                    "raw-state"
            );

            assertThat(outcome.redirectUrl()).isEqualTo("http://localhost:5173/login?error=google_failed");
            assertThat(outcome.refreshCookie()).isNull();
        }

        @Test
        @DisplayName("handleCallback redirects with google_failed when state is expired")
        void handleCallback_stateExpired_redirectsWithFailed() {
            OAuthLoginState expiredState = new OAuthLoginState(
                    "state-hash",
                    "nonce123",
                    "verifier123",
                    Instant.now().minusSeconds(10)
            );
            when(oAuthLoginStateRepository.findByStateHash(any())).thenReturn(Optional.of(expiredState));

            GoogleOAuthService.CallbackOutcome outcome = googleOAuthService.handleCallback(
                    "code123",
                    "raw-state",
                    "raw-state"
            );

            assertThat(outcome.redirectUrl()).isEqualTo("http://localhost:5173/login?error=google_failed");
            // Verify single-use state is deleted
            verify(oAuthLoginStateRepository).delete(expiredState);
        }
    }
}

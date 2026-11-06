package com.swiftdrop.auth.controller;
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
import com.swiftdrop.auth.google.dto.GoogleUserClaims;
import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.google.domain.OAuthLoginStateRepository;
import com.swiftdrop.auth.domain.RefreshTokenRepository;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.google.service.GoogleOAuthService;
import com.swiftdrop.auth.google.service.GoogleOidcVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration tests for Google OAuth 2.0 PKCE login (Flow 4.5).
 * Validates real PostgreSQL schema, Flyway migrations, and database constraints.
 * SRS v2.1 – Flow 4.5, FR-AUTH-009, FR-AUTH-010, FR-AUTH-011
 */
@Tag("integration")
@DisplayName("Google OAuth 2.0 Integration Tests")
class GoogleOAuthIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OAuthLoginStateRepository oAuthLoginStateRepository;

    @MockBean
    private GoogleOidcVerifier googleOidcVerifier;

    @SpyBean
    private GoogleOAuthService googleOAuthService;

    @BeforeEach
    void cleanUpDatabase() {
        // Prevent TestRestTemplate from following 302 redirects automatically
        restTemplate.getRestTemplate().setRequestFactory(new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        });

        refreshTokenRepository.deleteAll();
        oAuthLoginStateRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/v1/auth/config - returns googleEnabled: true (FR-AUTH-011)")
    void getAuthConfig_returnsConfiguredStatus() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/auth/config", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("googleEnabled")).isEqualTo(true);
    }

    @Test
    @DisplayName("GET /api/v1/auth/google/login - 302 redirects to Google and persists state to PostgreSQL (FR-AUTH-009)")
    void initiateLogin_redirectsAndPersistsStateInDb() {
        ResponseEntity<Void> response = restTemplate.getForEntity("/api/v1/auth/google/login", Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        URI location = response.getHeaders().getLocation();
        assertThat(location).isNotNull();
        assertThat(location.toString()).contains("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(location.toString()).contains("code_challenge_method=S256");

        // Validate sd_oauth cookie
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotEmpty();
        String oauthCookie = cookies.stream()
                .filter(c -> c.startsWith("sd_oauth="))
                .findFirst()
                .orElse(null);
        assertThat(oauthCookie).isNotNull();
        assertThat(oauthCookie).contains("HttpOnly");
        assertThat(oauthCookie).contains("Path=/api/v1/auth/google");
        assertThat(oauthCookie).contains("SameSite=Lax");

        // Verify state record persisted to PostgreSQL
        assertThat(oAuthLoginStateRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Flow 4.5 End-to-End: full Google OAuth login auto-registers new CUSTOMER")
    void completeOAuthFlow_newCustomer_createsUserAndSession() throws Exception {
        // Step 1: Initiate login
        ResponseEntity<Void> initResponse = restTemplate.getForEntity("/api/v1/auth/google/login", Void.class);
        assertThat(initResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);

        List<String> setCookies = initResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        String oauthCookieHeader = setCookies.stream()
                .filter(c -> c.startsWith("sd_oauth="))
                .findFirst()
                .orElseThrow();
        String cookieRawValue = oauthCookieHeader.split(";")[0].substring("sd_oauth=".length());

        // Step 2: Stub Google token exchange and OIDC verification
        doReturn("mock-google-id-token").when(googleOAuthService)
                .exchangeCodeForIdToken(any(), any());

        GoogleUserClaims claims = new GoogleUserClaims(
                "google-sub-kasun-101",
                "kasun.test@gmail.com",
                "Kasun Test",
                true
        );
        when(googleOidcVerifier.verifyIdToken(eq("mock-google-id-token"), any()))
                .thenReturn(claims);

        // Step 3: Callback from Google
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "sd_oauth=" + cookieRawValue);
        HttpEntity<Void> callbackEntity = new HttpEntity<>(headers);

        String callbackUrl = "/api/v1/auth/google/callback?code=mock-auth-code&state=" + cookieRawValue;
        ResponseEntity<Void> callbackResponse = restTemplate.exchange(
                callbackUrl,
                HttpMethod.GET,
                callbackEntity,
                Void.class
        );

        // Step 4: Verify redirect and cookies
        assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(callbackResponse.getHeaders().getLocation()).isNotNull();
        assertThat(callbackResponse.getHeaders().getLocation().toString()).isEqualTo("http://localhost:5173/auth/callback");

        List<String> callbackCookies = callbackResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(callbackCookies).isNotNull();

        // Check sd_refresh cookie set
        String refreshCookie = callbackCookies.stream()
                .filter(c -> c.startsWith("sd_refresh="))
                .findFirst()
                .orElse(null);
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie).contains("HttpOnly");
        assertThat(refreshCookie).contains("Path=/api/v1/auth");

        // Check sd_oauth cookie cleared
        String clearedOAuthCookie = callbackCookies.stream()
                .filter(c -> c.startsWith("sd_oauth="))
                .findFirst()
                .orElse(null);
        assertThat(clearedOAuthCookie).isNotNull();
        assertThat(clearedOAuthCookie).contains("Max-Age=0");

        // Step 5: Verify user created in PostgreSQL
        Optional<User> userOpt = userRepository.findByGoogleSub("google-sub-kasun-101");
        assertThat(userOpt).isPresent();
        User createdUser = userOpt.get();
        assertThat(createdUser.getEmail()).isEqualTo("kasun.test@gmail.com");
        assertThat(createdUser.getFullName()).isEqualTo("Kasun Test");
        assertThat(createdUser.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(createdUser.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(createdUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(createdUser.getPasswordHash()).isNull();

        // Step 6: Verify refresh token stored in PostgreSQL
        assertThat(refreshTokenRepository.count()).isEqualTo(1);

        // Step 7: Verify single-use OAuth state was consumed/deleted
        assertThat(oAuthLoginStateRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("FR-AUTH-010: Existing LOCAL account prevents takeover and returns error=account_exists")
    void callback_existingLocalAccount_rejectsWithAccountExists() throws Exception {
        // Step 1: Pre-populate local customer
        User localUser = new User();
        localUser.setEmail("kasun.perera@example.lk");
        localUser.setFullName("Kasun Perera");
        localUser.setPasswordHash("$2a$12$somevalidhashvaluehere");
        localUser.setAuthProvider(AuthProvider.LOCAL);
        localUser.setRole(UserRole.CUSTOMER);
        localUser.setStatus(UserStatus.ACTIVE);
        userRepository.saveAndFlush(localUser);

        // Step 2: Initiate login
        ResponseEntity<Void> initResponse = restTemplate.getForEntity("/api/v1/auth/google/login", Void.class);
        List<String> setCookies = initResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        String cookieRawValue = setCookies.stream()
                .filter(c -> c.startsWith("sd_oauth="))
                .findFirst()
                .orElseThrow()
                .split(";")[0].substring("sd_oauth=".length());

        // Step 3: Stub ID token for matching email but from Google
        doReturn("mock-google-id-token").when(googleOAuthService)
                .exchangeCodeForIdToken(any(), any());

        GoogleUserClaims claims = new GoogleUserClaims(
                "google-sub-attacker-999",
                "kasun.perera@example.lk",
                "Kasun Attacker",
                true
        );
        when(googleOidcVerifier.verifyIdToken(eq("mock-google-id-token"), any()))
                .thenReturn(claims);

        // Step 4: Callback
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "sd_oauth=" + cookieRawValue);
        HttpEntity<Void> callbackEntity = new HttpEntity<>(headers);

        String callbackUrl = "/api/v1/auth/google/callback?code=mock-auth-code&state=" + cookieRawValue;
        ResponseEntity<Void> callbackResponse = restTemplate.exchange(
                callbackUrl,
                HttpMethod.GET,
                callbackEntity,
                Void.class
        );

        // Step 5: Verify redirect with account_exists error
        assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(callbackResponse.getHeaders().getLocation()).isNotNull();
        assertThat(callbackResponse.getHeaders().getLocation().toString()).isEqualTo("http://localhost:5173/login?error=account_exists");

        // Verify local user was not overwritten
        User localUserAfter = userRepository.findByEmailIgnoreCase("kasun.perera@example.lk").orElseThrow();
        assertThat(localUserAfter.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(localUserAfter.getGoogleSub()).isNull();
        assertThat(localUserAfter.getPasswordHash()).isEqualTo("$2a$12$somevalidhashvaluehere");

        // No refresh token was created
        assertThat(refreshTokenRepository.count()).isEqualTo(0);
    }
}


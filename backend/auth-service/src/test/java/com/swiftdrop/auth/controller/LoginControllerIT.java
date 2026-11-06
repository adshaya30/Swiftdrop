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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.AbstractIntegrationTest;
import com.swiftdrop.auth.dto.request.LoginRequest;
import com.swiftdrop.auth.dto.request.RegistrationRequest;
import com.swiftdrop.auth.entity.*;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.RefreshTokenRepository;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.login.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for POST /api/v1/auth/login.
 * Uses real PostgreSQL via Testcontainers.
 * SRS v2.1 – FR-AUTH-003, FR-AUTH-004, FR-AUTH-005, FR-AUTH-006, FR-AUTH-007
 */
@Tag("integration")
@DisplayName("Login Integration Tests (Feature 2)")
class LoginControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";
    private static final String REGISTER_ENDPOINT = "/api/v1/auth/register";

    private User testUser;
    private final String rawPassword = "SecurePass123!";

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        merchantRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User();
        testUser.setFullName("Kasun Perera");
        testUser.setEmail("kasun.perera@example.lk");
        testUser.setPhone("+94771234567");
        testUser.setPasswordHash(passwordEncoder.encode(rawPassword));
        testUser.setRole(UserRole.CUSTOMER);
        testUser.setAuthProvider(AuthProvider.LOCAL);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setFailedLoginCount(0);
        testUser.setLockedUntil(null);
        testUser = userRepository.saveAndFlush(testUser);
    }

    @Test
    @DisplayName("1. Correct customer email/password -> 200 with JWT and user summary")
    void validLogin_returns200() {
        LoginRequest payload = new LoginRequest("kasun.perera@example.lk", rawPassword);
        ResponseEntity<Map> response = postLogin(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("rawtypes")
        Map body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("accessToken")).isNotNull();
        assertThat(body.get("tokenType")).isEqualTo("Bearer");
        assertThat(body.get("expiresIn")).isEqualTo(900);

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) body.get("user");
        assertThat(user).isNotNull();
        assertThat(user.get("id")).isEqualTo(testUser.getId().toString());
        assertThat(user.get("fullName")).isEqualTo("Kasun Perera");
        assertThat(user.get("role")).isEqualTo("CUSTOMER");
        assertThat(user.get("mustChangePassword")).isEqualTo(false);
    }

    @Test
    @DisplayName("2. Correct RS256 JWT claims and signature")
    void validLogin_issuesValidRS256Token() {
        LoginRequest payload = new LoginRequest("kasun.perera@example.lk", rawPassword);
        ResponseEntity<Map> response = postLogin(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = (String) response.getBody().get("accessToken");

        Map<String, Object> claims = jwtService.parseAndVerifyToken(token);
        assertThat(claims.get("sub")).isEqualTo(testUser.getId().toString());
        assertThat(claims.get("role")).isEqualTo("CUSTOMER");
        assertThat(claims.get("typ")).isEqualTo("user");
        assertThat(claims.get("iss")).isEqualTo("swiftdrop");
        assertThat(claims.get("aud")).isEqualTo("swiftdrop-api");
        assertThat(claims.get("jti")).isNotNull();
    }

    @Test
    @DisplayName("3. Invalid password -> 401 INVALID_CREDENTIALS")
    void invalidPassword_returns401() {
        LoginRequest payload = new LoginRequest("kasun.perera@example.lk", "WrongPassword!");
        ResponseEntity<Map> response = postLogin(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(response.getBody().get("detail")).isEqualTo("Invalid email or password");
    }

    @Test
    @DisplayName("4. Unknown email -> 401 with same safe message")
    void unknownEmail_returns401() {
        LoginRequest payload = new LoginRequest("unknown@example.lk", rawPassword);
        ResponseEntity<Map> response = postLogin(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(response.getBody().get("detail")).isEqualTo("Invalid email or password");
    }

    @Test
    @DisplayName("5. Google-only account with no local password -> 401")
    void googleOnlyAccount_returns401() {
        testUser.setPasswordHash(null);
        testUser.setAuthProvider(AuthProvider.GOOGLE);
        userRepository.saveAndFlush(testUser);

        LoginRequest payload = new LoginRequest("kasun.perera@example.lk", rawPassword);
        ResponseEntity<Map> response = postLogin(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("6 & 7. Five failed attempts lock account for 15 minutes; subsequent attempt -> 429 with Retry-After")
    void fiveFailedAttempts_lockAccountAndReturn429() {
        LoginRequest badPayload = new LoginRequest("kasun.perera@example.lk", "WrongPassword!");

        // 5 consecutive failed logins
        for (int i = 1; i <= 5; i++) {
            ResponseEntity<Map> res = postLogin(badPayload);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // 6th attempt -> 429 ACCOUNT_LOCKED with Retry-After header
        ResponseEntity<Map> lockedRes = postLogin(badPayload);
        assertThat(lockedRes.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(lockedRes.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
        assertThat(lockedRes.getBody().get("code")).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    @DisplayName("8. Correct password while locked is rejected with 429")
    void correctPasswordWhileLocked_returns429() {
        testUser.setLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
        testUser.setFailedLoginCount(5);
        userRepository.saveAndFlush(testUser);

        LoginRequest validPayload = new LoginRequest("kasun.perera@example.lk", rawPassword);
        ResponseEntity<Map> response = postLogin(validPayload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    @DisplayName("9. Successful login resets failed login counters")
    void successfulLogin_resetsFailedLoginCounters() {
        testUser.setFailedLoginCount(3);
        userRepository.saveAndFlush(testUser);

        LoginRequest validPayload = new LoginRequest("kasun.perera@example.lk", rawPassword);
        ResponseEntity<Map> response = postLogin(validPayload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        User reloaded = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(reloaded.getFailedLoginCount()).isEqualTo(0);
        assertThat(reloaded.getLockedUntil()).isNull();
        assertThat(reloaded.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("10. DEACTIVATED account -> 403 ACCOUNT_DEACTIVATED after valid credentials")
    void deactivatedAccount_returns403() {
        testUser.setStatus(UserStatus.DEACTIVATED);
        userRepository.saveAndFlush(testUser);

        LoginRequest validPayload = new LoginRequest("kasun.perera@example.lk", rawPassword);
        ResponseEntity<Map> response = postLogin(validPayload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("ACCOUNT_DEACTIVATED");
    }

    @Test
    @DisplayName("11. PENDING, REJECTED, SUSPENDED merchant errors -> 403")
    void merchantStatusGate_returns403() {
        testUser.setRole(UserRole.MERCHANT);
        userRepository.saveAndFlush(testUser);

        Merchant merchant = new Merchant(testUser.getId(), "Acme Corp", MerchantStatus.PENDING);
        merchantRepository.saveAndFlush(merchant);

        LoginRequest payload = new LoginRequest("kasun.perera@example.lk", rawPassword);

        // PENDING
        ResponseEntity<Map> resPending = postLogin(payload);
        assertThat(resPending.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resPending.getBody().get("code")).isEqualTo("MERCHANT_PENDING");

        // REJECTED
        merchant.setStatus(MerchantStatus.REJECTED);
        merchantRepository.saveAndFlush(merchant);
        ResponseEntity<Map> resRejected = postLogin(payload);
        assertThat(resRejected.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resRejected.getBody().get("code")).isEqualTo("MERCHANT_REJECTED");

        // SUSPENDED
        merchant.setStatus(MerchantStatus.SUSPENDED);
        merchantRepository.saveAndFlush(merchant);
        ResponseEntity<Map> resSuspended = postLogin(payload);
        assertThat(resSuspended.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resSuspended.getBody().get("code")).isEqualTo("MERCHANT_SUSPENDED");

        // APPROVED succeeds
        merchant.setStatus(MerchantStatus.APPROVED);
        merchantRepository.saveAndFlush(merchant);
        ResponseEntity<Map> resApproved = postLogin(payload);
        assertThat(resApproved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resApproved.getBody().get("user")).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) resApproved.getBody().get("user");
        assertThat(user.get("merchantStatus")).isEqualTo("APPROVED");
        assertThat(user.get("merchantId")).isEqualTo(merchant.getId().toString());
    }

    @Test
    @DisplayName("12. No password or sensitive data in responses or JWTs")
    void noSensitiveDataInLoginResponse() {
        LoginRequest payload = new LoginRequest("kasun.perera@example.lk", rawPassword);
        ResponseEntity<Map> response = postLogin(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = response.getBody();
        assertThat(body.containsKey("password")).isFalse();
        assertThat(body.containsKey("passwordHash")).isFalse();

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) body.get("user");
        assertThat(user.containsKey("password")).isFalse();
        assertThat(user.containsKey("passwordHash")).isFalse();
        assertThat(user.containsKey("phone")).isFalse();
        assertThat(user.containsKey("email")).isFalse();
    }

    @Test
    @DisplayName("13. Refresh cookie is HttpOnly with required settings")
    void refreshCookie_isHttpOnlyAndConfigured() {
        LoginRequest payload = new LoginRequest("kasun.perera@example.lk", rawPassword);
        ResponseEntity<Map> response = postLogin(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull().isNotEmpty();

        String cookie = cookies.get(0);
        assertThat(cookie).contains("sd_refresh=");
        assertThat(cookie.toLowerCase()).contains("httponly");
        assertThat(cookie).contains("Path=/api/v1/auth");
        assertThat(cookie).contains("SameSite=Strict");
    }

    @Test
    @DisplayName("14. Only SHA-256 refresh-token hash is stored in database")
    void onlySha256HashStored() {
        LoginRequest payload = new LoginRequest("kasun.perera@example.lk", rawPassword);
        ResponseEntity<Map> response = postLogin(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<RefreshToken> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        RefreshToken stored = tokens.get(0);

        assertThat(stored.getTokenHash()).hasSize(64); // 64 hex characters
        assertThat(stored.getUserId()).isEqualTo(testUser.getId());
        assertThat(stored.getFamilyId()).isNotNull();
    }

    @Test
    @DisplayName("15. Regression: Registration still works after login feature added")
    void regression_registrationStillWorks() {
        RegistrationRequest req = new RegistrationRequest(
                "Nimal Silva",
                "nimal.silva@example.lk",
                "0712345678",
                "Str0ng!Pass#1"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RegistrationRequest> entity = new HttpEntity<>(req, headers);

        ResponseEntity<Map> res = restTemplate.postForEntity(REGISTER_ENDPOINT, entity, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().get("status")).isEqualTo("ACTIVE");
        assertThat(res.getBody().get("email")).isEqualTo("nimal.silva@example.lk");
    }

    private ResponseEntity<Map> postLogin(LoginRequest payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LoginRequest> entity = new HttpEntity<>(payload, headers);
        return restTemplate.postForEntity(LOGIN_ENDPOINT, entity, Map.class);
    }
}


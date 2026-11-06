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
import com.swiftdrop.auth.dto.response.LoginResponse;
import com.swiftdrop.auth.entity.*;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.RefreshTokenRepository;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.login.service.JwtService;
import com.swiftdrop.auth.login.service.RefreshTokenService;
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
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for Refresh Token Rotation (FR-AUTH-007) and Logout (FR-AUTH-008).
 * Uses real PostgreSQL via Testcontainers.
 * SRS v2.1 – FR-AUTH-007, FR-AUTH-008, Section 3.6, Section 6.3
 */
@Tag("integration")
@DisplayName("Refresh Token Rotation and Logout Integration Tests (Feature 3)")
class RefreshAndLogoutIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";
    private static final String REFRESH_ENDPOINT = "/api/v1/auth/refresh";
    private static final String LOGOUT_ENDPOINT = "/api/v1/auth/logout";
    private static final String HEADER_X_REQUESTED_WITH = "X-Requested-With";
    private static final String HEADER_SWIFTDROP = "SwiftDrop";

    private User testUser;
    private final String rawPassword = "SecurePass123!";

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        merchantRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User();
        testUser.setFullName("Saman Kumara");
        testUser.setEmail("saman.kumara@example.lk");
        testUser.setPhone("+94779876543");
        testUser.setPasswordHash(passwordEncoder.encode(rawPassword));
        testUser.setRole(UserRole.CUSTOMER);
        testUser.setAuthProvider(AuthProvider.LOCAL);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setFailedLoginCount(0);
        testUser = userRepository.saveAndFlush(testUser);
    }

    @Test
    @DisplayName("1-5. Valid refresh rotates token, marks previous used, preserves family, stores SHA-256 and sets cookie")
    void validRefresh_rotatesTokenSuccessfully() {
        // Step 1: Login to get initial session
        ResponseEntity<Map> loginRes = postLogin("saman.kumara@example.lk", rawPassword);
        assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        String initialCookie = loginRes.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(initialCookie).isNotNull();
        String initialRawToken = extractCookieValue(initialCookie, "sd_refresh");

        // Step 2: Call /refresh with initial token
        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.set(HEADER_X_REQUESTED_WITH, HEADER_SWIFTDROP);
        refreshHeaders.add(HttpHeaders.COOKIE, "sd_refresh=" + initialRawToken);
        HttpEntity<Void> refreshEntity = new HttpEntity<>(null, refreshHeaders);

        ResponseEntity<Map> refreshRes = restTemplate.postForEntity(REFRESH_ENDPOINT, refreshEntity, Map.class);

        assertThat(refreshRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshRes.getBody().get("accessToken")).isNotNull();
        assertThat(refreshRes.getBody().get("tokenType")).isEqualTo("Bearer");
        assertThat(refreshRes.getBody().get("expiresIn")).isEqualTo(900);

        // Check new cookie set
        String rotatedCookie = refreshRes.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(rotatedCookie).isNotNull();
        assertThat(rotatedCookie).contains("HttpOnly");
        assertThat(rotatedCookie).contains("SameSite=Strict");
        assertThat(rotatedCookie).contains("Path=/api/v1/auth");
        String rotatedRawToken = extractCookieValue(rotatedCookie, "sd_refresh");
        assertThat(rotatedRawToken).isNotEqualTo(initialRawToken);

        // Check DB state
        List<RefreshToken> allTokens = refreshTokenRepository.findAll();
        assertThat(allTokens).hasSize(2);

        String initialHash = refreshTokenService.sha256Hex(initialRawToken);
        RefreshToken initialDbToken = refreshTokenRepository.findByTokenHash(initialHash).orElseThrow();
        assertThat(initialDbToken.getUsedAt()).isNotNull();

        String rotatedHash = refreshTokenService.sha256Hex(rotatedRawToken);
        RefreshToken rotatedDbToken = refreshTokenRepository.findByTokenHash(rotatedHash).orElseThrow();
        assertThat(rotatedDbToken.getUsedAt()).isNull();

        // Must remain in the exact same family and preserve family_created_at
        assertThat(rotatedDbToken.getFamilyId()).isEqualTo(initialDbToken.getFamilyId());
        assertThat(rotatedDbToken.getFamilyCreatedAt()).isEqualTo(initialDbToken.getFamilyCreatedAt());
    }

    @Test
    @DisplayName("6. Missing X-Requested-With header on refresh returns 400")
    void refresh_missingHeader_returns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "sd_refresh=some-token");
        HttpEntity<Void> entity = new HttpEntity<>(null, headers);

        ResponseEntity<Map> res = restTemplate.postForEntity(REFRESH_ENDPOINT, entity, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("code")).isEqualTo("INVALID_REQUEST");
    }

    @Test
    @DisplayName("7. Missing or invalid refresh token returns 401 UNAUTHENTICATED")
    void refresh_missingOrInvalidToken_returns401() {
        // Missing cookie
        HttpHeaders headers1 = new HttpHeaders();
        headers1.set(HEADER_X_REQUESTED_WITH, HEADER_SWIFTDROP);
        HttpEntity<Void> entity1 = new HttpEntity<>(null, headers1);

        ResponseEntity<Map> res1 = restTemplate.postForEntity(REFRESH_ENDPOINT, entity1, Map.class);
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res1.getBody().get("code")).isEqualTo("UNAUTHENTICATED");

        // Invalid unknown cookie
        HttpHeaders headers2 = new HttpHeaders();
        headers2.set(HEADER_X_REQUESTED_WITH, HEADER_SWIFTDROP);
        headers2.add(HttpHeaders.COOKIE, "sd_refresh=invalid-unknown-token");
        HttpEntity<Void> entity2 = new HttpEntity<>(null, headers2);

        ResponseEntity<Map> res2 = restTemplate.postForEntity(REFRESH_ENDPOINT, entity2, Map.class);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res2.getBody().get("code")).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    @DisplayName("8. Re-presenting used token within 10s grace returns fresh access token without new cookie")
    void refresh_usedTokenWithinGrace_returnsTokenWithoutNewCookie() {
        // Login & rotate once
        ResponseEntity<Map> loginRes = postLogin("saman.kumara@example.lk", rawPassword);
        String rawToken = extractCookieValue(loginRes.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "sd_refresh");

        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.set(HEADER_X_REQUESTED_WITH, HEADER_SWIFTDROP);
        refreshHeaders.add(HttpHeaders.COOKIE, "sd_refresh=" + rawToken);

        // First rotation (valid)
        ResponseEntity<Map> firstRot = restTemplate.postForEntity(REFRESH_ENDPOINT, new HttpEntity<>(null, refreshHeaders), Map.class);
        assertThat(firstRot.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Re-presenting the SAME initial token immediately (within 10s grace window)
        ResponseEntity<Map> secondRot = restTemplate.postForEntity(REFRESH_ENDPOINT, new HttpEntity<>(null, refreshHeaders), Map.class);
        assertThat(secondRot.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondRot.getBody().get("accessToken")).isNotNull();
        // Cookie header must NOT be present in response
        assertThat(secondRot.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    @DisplayName("9. Re-presenting used token after 10s grace window revokes entire family (Replay Detected)")
    void refresh_replayAfterGrace_revokesFamilyAndReturns401() {
        // Login & rotate
        ResponseEntity<Map> loginRes = postLogin("saman.kumara@example.lk", rawPassword);
        String rawToken = extractCookieValue(loginRes.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "sd_refresh");

        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.set(HEADER_X_REQUESTED_WITH, HEADER_SWIFTDROP);
        refreshHeaders.add(HttpHeaders.COOKIE, "sd_refresh=" + rawToken);

        restTemplate.postForEntity(REFRESH_ENDPOINT, new HttpEntity<>(null, refreshHeaders), Map.class);

        // Backdate the used_at of the used token to 15 seconds ago (> 10s grace)
        String hash = refreshTokenService.sha256Hex(rawToken);
        RefreshToken usedToken = refreshTokenRepository.findByTokenHash(hash).orElseThrow();
        usedToken.setUsedAt(Instant.now().minus(15, ChronoUnit.SECONDS));
        refreshTokenRepository.saveAndFlush(usedToken);

        // Now attempt to refresh with the replayed token
        ResponseEntity<Map> replayRes = restTemplate.postForEntity(REFRESH_ENDPOINT, new HttpEntity<>(null, refreshHeaders), Map.class);
        assertThat(replayRes.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(replayRes.getBody().get("code")).isEqualTo("UNAUTHENTICATED");

        // Verify entire family is revoked
        assertThat(refreshTokenRepository.isFamilyRevoked(usedToken.getFamilyId())).isTrue();
    }

    @Test
    @DisplayName("10. Revoked token family cannot refresh")
    void refresh_revokedFamily_returns401() {
        ResponseEntity<Map> loginRes = postLogin("saman.kumara@example.lk", rawPassword);
        String rawToken = extractCookieValue(loginRes.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "sd_refresh");

        String hash = refreshTokenService.sha256Hex(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash).orElseThrow();
        refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now());

        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.set(HEADER_X_REQUESTED_WITH, HEADER_SWIFTDROP);
        refreshHeaders.add(HttpHeaders.COOKIE, "sd_refresh=" + rawToken);

        ResponseEntity<Map> res = restTemplate.postForEntity(REFRESH_ENDPOINT, new HttpEntity<>(null, refreshHeaders), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("11. Expired token cannot refresh")
    void refresh_expiredToken_returns401() {
        ResponseEntity<Map> loginRes = postLogin("saman.kumara@example.lk", rawPassword);
        String rawToken = extractCookieValue(loginRes.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "sd_refresh");

        String hash = refreshTokenService.sha256Hex(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash).orElseThrow();
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        refreshTokenRepository.saveAndFlush(token);

        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.set(HEADER_X_REQUESTED_WITH, HEADER_SWIFTDROP);
        refreshHeaders.add(HttpHeaders.COOKIE, "sd_refresh=" + rawToken);

        ResponseEntity<Map> res = restTemplate.postForEntity(REFRESH_ENDPOINT, new HttpEntity<>(null, refreshHeaders), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("12. Family older than 30 days cannot refresh")
    void refresh_familyOlderThan30Days_returns401() {
        ResponseEntity<Map> loginRes = postLogin("saman.kumara@example.lk", rawPassword);
        String rawToken = extractCookieValue(loginRes.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "sd_refresh");

        String hash = refreshTokenService.sha256Hex(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash).orElseThrow();
        token.setFamilyCreatedAt(Instant.now().minus(31, ChronoUnit.DAYS));
        refreshTokenRepository.saveAndFlush(token);

        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.set(HEADER_X_REQUESTED_WITH, HEADER_SWIFTDROP);
        refreshHeaders.add(HttpHeaders.COOKIE, "sd_refresh=" + rawToken);

        ResponseEntity<Map> res = restTemplate.postForEntity(REFRESH_ENDPOINT, new HttpEntity<>(null, refreshHeaders), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("13. Deactivated user cannot refresh")
    void refresh_deactivatedUser_returns401() {
        ResponseEntity<Map> loginRes = postLogin("saman.kumara@example.lk", rawPassword);
        String rawToken = extractCookieValue(loginRes.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "sd_refresh");

        testUser.setStatus(UserStatus.DEACTIVATED);
        userRepository.saveAndFlush(testUser);

        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.set(HEADER_X_REQUESTED_WITH, HEADER_SWIFTDROP);
        refreshHeaders.add(HttpHeaders.COOKIE, "sd_refresh=" + rawToken);

        ResponseEntity<Map> res = restTemplate.postForEntity(REFRESH_ENDPOINT, new HttpEntity<>(null, refreshHeaders), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("14. Non-APPROVED merchant cannot refresh")
    void refresh_nonApprovedMerchant_returns401() {
        testUser.setRole(UserRole.MERCHANT);
        userRepository.saveAndFlush(testUser);

        Merchant merchant = new Merchant();
        merchant.setUserId(testUser.getId());
        merchant.setBusinessName("Fresh Organic Foods");
        merchant.setStatus(MerchantStatus.APPROVED);
        merchantRepository.saveAndFlush(merchant);

        ResponseEntity<Map> loginRes = postLogin("saman.kumara@example.lk", rawPassword);
        String rawToken = extractCookieValue(loginRes.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "sd_refresh");

        // Merchant gets suspended
        merchant.setStatus(MerchantStatus.SUSPENDED);
        merchantRepository.saveAndFlush(merchant);

        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.set(HEADER_X_REQUESTED_WITH, HEADER_SWIFTDROP);
        refreshHeaders.add(HttpHeaders.COOKIE, "sd_refresh=" + rawToken);

        ResponseEntity<Map> res = restTemplate.postForEntity(REFRESH_ENDPOINT, new HttpEntity<>(null, refreshHeaders), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("15. Concurrent refresh calls are safely handled without race corruptions")
    void concurrentRefresh_safelyHandled() throws Exception {
        ResponseEntity<Map> loginRes = postLogin("saman.kumara@example.lk", rawPassword);
        String rawToken = extractCookieValue(loginRes.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "sd_refresh");

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                HttpHeaders h = new HttpHeaders();
                h.set(HEADER_X_REQUESTED_WITH, HEADER_SWIFTDROP);
                h.add(HttpHeaders.COOKIE, "sd_refresh=" + rawToken);
                return restTemplate.postForEntity(REFRESH_ENDPOINT, new HttpEntity<>(null, h), Map.class);
            }));
        }

        latch.countDown(); // Trigger all threads simultaneously

        List<ResponseEntity<Map>> responses = new ArrayList<>();
        for (Future<ResponseEntity<Map>> f : futures) {
            responses.add(f.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // All calls should succeed (one rotation + grace responses), with 0 unhandled 500 exceptions
        for (ResponseEntity<Map> res : responses) {
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(res.getBody().get("accessToken")).isNotNull();
        }
    }

    @Test
    @DisplayName("16-18. Logout returns 204, clears cookie, and invalidates future refresh")
    void logout_clearsCookieAndRevokesFamily() {
        // Step 1: Login
        ResponseEntity<Map> loginRes = postLogin("saman.kumara@example.lk", rawPassword);
        String rawToken = extractCookieValue(loginRes.getHeaders().getFirst(HttpHeaders.SET_COOKIE), "sd_refresh");

        // Step 2: Logout with cookie
        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.set(HEADER_X_REQUESTED_WITH, HEADER_SWIFTDROP);
        logoutHeaders.add(HttpHeaders.COOKIE, "sd_refresh=" + rawToken);

        ResponseEntity<Void> logoutRes = restTemplate.postForEntity(LOGOUT_ENDPOINT, new HttpEntity<>(null, logoutHeaders), Void.class);
        assertThat(logoutRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        String clearCookie = logoutRes.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(clearCookie).contains("Max-Age=0");

        // Step 3: Refresh after logout must fail with 401
        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.set(HEADER_X_REQUESTED_WITH, HEADER_SWIFTDROP);
        refreshHeaders.add(HttpHeaders.COOKIE, "sd_refresh=" + rawToken);

        ResponseEntity<Map> refreshRes = restTemplate.postForEntity(REFRESH_ENDPOINT, new HttpEntity<>(null, refreshHeaders), Map.class);
        assertThat(refreshRes.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Step 4: Logout without cookie also returns 204
        HttpHeaders emptyLogoutHeaders = new HttpHeaders();
        emptyLogoutHeaders.set(HEADER_X_REQUESTED_WITH, HEADER_SWIFTDROP);
        ResponseEntity<Void> logoutRes2 = restTemplate.postForEntity(LOGOUT_ENDPOINT, new HttpEntity<>(null, emptyLogoutHeaders), Void.class);
        assertThat(logoutRes2.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private ResponseEntity<Map> postLogin(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        LoginRequest req = new LoginRequest(email, password);
        return restTemplate.postForEntity(LOGIN_ENDPOINT, new HttpEntity<>(req, headers), Map.class);
    }

    private String extractCookieValue(String setCookieHeader, String cookieName) {
        if (setCookieHeader == null) return null;
        for (String part : setCookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(cookieName + "=")) {
                return trimmed.substring((cookieName + "=").length());
            }
        }
        return null;
    }
}


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
import com.swiftdrop.auth.profile.dto.ChangePasswordRequest;
import com.swiftdrop.auth.profile.dto.UpdateProfileRequest;
import com.swiftdrop.auth.profile.dto.UserProfileResponse;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.RefreshToken;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.RefreshTokenRepository;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.entity.AuthProvider;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for Profile Management and Password Change.
 * Backed by real PostgreSQL 16 Testcontainer.
 * SRS v2.1 – FR-AUTH-012, FR-AUTH-013, FR-AUTH-014
 */
@Tag("integration")
@DisplayName("Profile & Password Change Integration Tests")
class ProfileIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        merchantRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createLocalUser(String email, String rawPassword, boolean mustChangePassword) {
        User user = new User();
        user.setFullName("Nuwan Fernando");
        user.setEmail(email.toLowerCase().trim());
        user.setPhone("+94771234567");
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.CUSTOMER);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(mustChangePassword);
        return userRepository.save(user);
    }

    private User createGoogleUser(String email) {
        User user = new User();
        user.setFullName("Google User");
        user.setEmail(email.toLowerCase().trim());
        user.setPhone("+94779998877");
        user.setPasswordHash(null);
        user.setGoogleSub("google-sub-12345");
        user.setRole(UserRole.CUSTOMER);
        user.setAuthProvider(AuthProvider.GOOGLE);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        return userRepository.save(user);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/me (FR-AUTH-012)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/me returns authenticated user's profile")
    void getProfile_e2e_success() {
        User user = createLocalUser("nuwan@example.lk", "Secret#2026", false);
        String token = jwtService.issueAccessToken(user.getId(), user.getRole().name(), false, null);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<UserProfileResponse> response = restTemplate.exchange(
                "/api/v1/me",
                HttpMethod.GET,
                entity,
                UserProfileResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserProfileResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isEqualTo(user.getId());
        assertThat(body.email()).isEqualTo("nuwan@example.lk");
        assertThat(body.fullName()).isEqualTo("Nuwan Fernando");
        assertThat(body.role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(body.authProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(body.mustChangePassword()).isFalse();
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/me (FR-AUTH-012)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PUT /api/v1/me updates profile fields in database")
    void updateProfile_e2e_success() {
        User user = createLocalUser("nuwan@example.lk", "Secret#2026", false);
        String token = jwtService.issueAccessToken(user.getId(), user.getRole().name(), false, null);

        UpdateProfileRequest updateRequest = new UpdateProfileRequest("Nuwan K. Fernando", "+94779876543");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UpdateProfileRequest> entity = new HttpEntity<>(updateRequest, headers);

        ResponseEntity<UserProfileResponse> response = restTemplate.exchange(
                "/api/v1/me",
                HttpMethod.PUT,
                entity,
                UserProfileResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserProfileResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.fullName()).isEqualTo("Nuwan K. Fernando");
        assertThat(body.phone()).isEqualTo("+94779876543");

        // Verify DB state
        User dbUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(dbUser.getFullName()).isEqualTo("Nuwan K. Fernando");
        assertThat(dbUser.getPhone()).isEqualTo("+94779876543");
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/me/change-password (FR-AUTH-013)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/me/change-password updates password, clears forced flag, revokes tokens, clears cookie")
    void changePassword_e2e_success() {
        User user = createLocalUser("nuwan@example.lk", "OldPassword#123", true);

        // Create an active refresh token session for the user
        RefreshToken rt = new RefreshToken(
                user.getId(),
                UUID.randomUUID(),
                "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0",
                Instant.now().plusSeconds(604800),
                "TestAgent",
                "127.0.0.1"
        );
        refreshTokenRepository.save(rt);

        String token = jwtService.issueAccessToken(user.getId(), user.getRole().name(), true, null);

        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword#123", "NewSecret#2026");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ChangePasswordRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/me/change-password",
                HttpMethod.POST,
                entity,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify Set-Cookie clears sd_refresh
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("sd_refresh=");
        assertThat(setCookie).contains("Max-Age=0");

        // Verify DB password updated and mustChangePassword cleared
        User dbUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewSecret#2026", dbUser.getPasswordHash())).isTrue();
        assertThat(dbUser.isMustChangePassword()).isFalse();

        // Verify refresh token revoked
        RefreshToken dbRt = refreshTokenRepository.findById(rt.getId()).orElseThrow();
        assertThat(dbRt.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("POST /api/v1/me/change-password rejects Google OAuth accounts with 400")
    void changePassword_googleAccount_returns400() {
        User googleUser = createGoogleUser("googleuser@example.com");
        String token = jwtService.issueAccessToken(googleUser.getId(), googleUser.getRole().name(), false, null);

        ChangePasswordRequest request = new ChangePasswordRequest("AnyPassword#123", "NewSecret#2026");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ChangePasswordRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/me/change-password",
                HttpMethod.POST,
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("SOCIAL_ACCOUNT_PASSWORD_NOT_SUPPORTED");
    }

    // -------------------------------------------------------------------------
    // FR-AUTH-014 Forced Password Change Gate
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("FR-AUTH-014: pwc=true permits GET /me and POST /me/change-password, blocks PUT /me")
    void forcedPasswordChange_gate_e2e() {
        User user = createLocalUser("gate.test@example.lk", "InitialTemp#123", true);
        String tokenWithPwc = jwtService.issueAccessToken(user.getId(), user.getRole().name(), true, null);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenWithPwc);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 1. GET /api/v1/me is allowed
        ResponseEntity<UserProfileResponse> getResponse = restTemplate.exchange(
                "/api/v1/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                UserProfileResponse.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 2. PUT /api/v1/me is blocked
        UpdateProfileRequest updateReq = new UpdateProfileRequest("New Name", "+94771234567");
        ResponseEntity<Map> putResponse = restTemplate.exchange(
                "/api/v1/me",
                HttpMethod.PUT,
                new HttpEntity<>(updateReq, headers),
                Map.class
        );
        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(putResponse.getBody()).isNotNull();
        assertThat(putResponse.getBody().get("code")).isEqualTo("PASSWORD_CHANGE_REQUIRED");

        // 3. POST /api/v1/me/change-password is allowed
        ChangePasswordRequest cpReq = new ChangePasswordRequest("InitialTemp#123", "PermanentPass#2026");
        ResponseEntity<Void> cpResponse = restTemplate.exchange(
                "/api/v1/me/change-password",
                HttpMethod.POST,
                new HttpEntity<>(cpReq, headers),
                Void.class
        );
        assertThat(cpResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}

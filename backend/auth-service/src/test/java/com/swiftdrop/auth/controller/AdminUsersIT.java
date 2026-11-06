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
import com.swiftdrop.auth.client.DeliveryServiceClient;
import com.swiftdrop.auth.dto.admin.CreateUserRequest;
import com.swiftdrop.auth.dto.admin.CreateUserResponse;
import com.swiftdrop.auth.dto.admin.ResetPasswordResponse;
import com.swiftdrop.auth.entity.*;
import com.swiftdrop.auth.repository.*;
import com.swiftdrop.auth.login.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration tests for Admin User Management and Audit/Outbox events.
 * Backed by real PostgreSQL 16 Testcontainer.
 * SRS v2.1 – FR-AUTH-015, FR-AUTH-020, FR-AUTH-021, FR-AUTH-023
 */
@Tag("integration")
@DisplayName("Admin User Management Integration Tests")
class AdminUsersIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourierProfileRepository courierProfileRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DeliveryServiceClient deliveryServiceClient;

    private User adminUser;
    private String adminToken;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        courierProfileRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        merchantRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = new User();
        adminUser.setFullName("Super Admin");
        adminUser.setEmail("superadmin@swiftdrop.lk");
        adminUser.setPhone("+94770000001");
        adminUser.setPasswordHash(passwordEncoder.encode("AdminPass123!"));
        adminUser.setRole(UserRole.ADMIN);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setAuthProvider(AuthProvider.LOCAL);
        adminUser.setMustChangePassword(false);
        adminUser = userRepository.save(adminUser);

        adminToken = jwtService.issueAccessToken(adminUser.getId(), adminUser.getRole().name(), false, null);
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("Admin can create new ADMIN user, and audit outbox event is persisted")
    void adminCanCreateAdminUser_andAuditOutboxPersisted() {
        CreateUserRequest request = new CreateUserRequest(
                "Second Admin",
                "secondadmin@swiftdrop.lk",
                "+94771234567",
                "ADMIN",
                null,
                null
        );

        HttpEntity<CreateUserRequest> entity = new HttpEntity<>(request, createAuthHeaders());
        ResponseEntity<CreateUserResponse> response = restTemplate.exchange(
                "/api/v1/admin/users",
                HttpMethod.POST,
                entity,
                CreateUserResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().temporaryPassword()).hasSize(16);
        assertThat(response.getBody().user().email()).isEqualTo("secondadmin@swiftdrop.lk");

        // Verify database user
        User createdDbUser = userRepository.findByEmailIgnoreCase("secondadmin@swiftdrop.lk").orElseThrow();
        assertThat(createdDbUser.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(createdDbUser.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches(response.getBody().temporaryPassword(), createdDbUser.getPasswordHash())).isTrue();

        // Verify outbox audit event
        List<OutboxEvent> outboxEvents = outboxEventRepository.findByEventType("audit.recorded");
        assertThat(outboxEvents).isNotEmpty();
        OutboxEvent audit = outboxEvents.get(0);
        assertThat(audit.getPayload()).contains("USER_CREATED");
        assertThat(audit.getPayload()).contains("secondadmin@swiftdrop.lk");
    }

    @Test
    @DisplayName("Admin can create new COURIER with CourierProfile, and both audit and courier.changed outbox events are persisted")
    void adminCanCreateCourier_withProfileAndOutboxEvents() {
        CreateUserRequest request = new CreateUserRequest(
                "Sunil Courier",
                "sunil@swiftdrop.lk",
                "+94778889900",
                "COURIER",
                "MOTORCYCLE",
                "WP-BA-1234"
        );

        HttpEntity<CreateUserRequest> entity = new HttpEntity<>(request, createAuthHeaders());
        ResponseEntity<CreateUserResponse> response = restTemplate.exchange(
                "/api/v1/admin/users",
                HttpMethod.POST,
                entity,
                CreateUserResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();

        // Verify courier profile exists
        User courierUser = userRepository.findByEmailIgnoreCase("sunil@swiftdrop.lk").orElseThrow();
        CourierProfile profile = courierProfileRepository.findByUserId(courierUser.getId()).orElseThrow();
        assertThat(profile.getVehicleType()).isEqualTo("MOTORCYCLE");
        assertThat(profile.getVehicleNumber()).isEqualTo("WP-BA-1234");
        assertThat(profile.getStatus()).isEqualTo("ACTIVE");

        // Verify outbox events (both audit.recorded and courier.changed)
        List<OutboxEvent> auditEvents = outboxEventRepository.findByEventType("audit.recorded");
        List<OutboxEvent> courierEvents = outboxEventRepository.findByEventType("courier.changed");
        assertThat(auditEvents).isNotEmpty();
        assertThat(courierEvents).isNotEmpty();

        OutboxEvent courierEvent = courierEvents.get(0);
        assertThat(courierEvent.getAggregateType()).isEqualTo("COURIER");
        assertThat(courierEvent.getAggregateId()).isEqualTo(profile.getId());
        assertThat(courierEvent.getPayload()).contains("WP-BA-1234");
    }

    @Test
    @DisplayName("Admin can deactivate courier when active deliveries == 0")
    void deactivateCourier_success_whenNoActiveDeliveries() {
        User courier = new User();
        courier.setFullName("Active Courier");
        courier.setEmail("courier@swiftdrop.lk");
        courier.setPhone("+94775556677");
        courier.setPasswordHash(passwordEncoder.encode("Pass123!"));
        courier.setRole(UserRole.COURIER);
        courier.setStatus(UserStatus.ACTIVE);
        courier.setAuthProvider(AuthProvider.LOCAL);
        courier.setMustChangePassword(false);
        courier = userRepository.save(courier);

        CourierProfile profile = new CourierProfile();
        profile.setUserId(courier.getId());
        profile.setVehicleType("VAN");
        profile.setVehicleNumber("WP-CA-9988");
        profile.setStatus("ACTIVE");
        courierProfileRepository.save(profile);

        when(deliveryServiceClient.getActiveDeliveriesCount(profile.getId())).thenReturn(0);

        HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/admin/users/" + courier.getId() + "/deactivate",
                HttpMethod.POST,
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        User updated = userRepository.findById(courier.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(UserStatus.DEACTIVATED);

        CourierProfile updatedProfile = courierProfileRepository.findByUserId(courier.getId()).orElseThrow();
        assertThat(updatedProfile.getStatus()).isEqualTo("DEACTIVATED");

        // Verify courier.changed outbox event
        List<OutboxEvent> courierEvents = outboxEventRepository.findByEventType("courier.changed");
        assertThat(courierEvents).isNotEmpty();
        assertThat(courierEvents.get(0).getPayload()).contains("DEACTIVATED");
    }

    @Test
    @DisplayName("Deactivating courier with active deliveries returns 409 Conflict")
    void deactivateCourier_fails409_whenActiveDeliveriesExist() {
        User courier = new User();
        courier.setFullName("Busy Courier");
        courier.setEmail("busy@swiftdrop.lk");
        courier.setPhone("+94775556688");
        courier.setPasswordHash(passwordEncoder.encode("Pass123!"));
        courier.setRole(UserRole.COURIER);
        courier.setStatus(UserStatus.ACTIVE);
        courier.setAuthProvider(AuthProvider.LOCAL);
        courier.setMustChangePassword(false);
        courier = userRepository.save(courier);

        CourierProfile profile = new CourierProfile();
        profile.setUserId(courier.getId());
        profile.setVehicleType("VAN");
        profile.setVehicleNumber("WP-CA-9988");
        profile.setStatus("ACTIVE");
        courierProfileRepository.save(profile);

        when(deliveryServiceClient.getActiveDeliveriesCount(profile.getId())).thenReturn(2);

        HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/admin/users/" + courier.getId() + "/deactivate",
                HttpMethod.POST,
                entity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("COURIER_HAS_ACTIVE_DELIVERIES");

        User unchanged = userRepository.findById(courier.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Admin can reset user password, revoking refresh tokens and setting mustChangePassword")
    void adminCanResetPassword() {
        User target = new User();
        target.setFullName("Target User");
        target.setEmail("target@swiftdrop.lk");
        target.setPhone("+94779991122");
        target.setPasswordHash(passwordEncoder.encode("OldSecret123!"));
        target.setRole(UserRole.CUSTOMER);
        target.setStatus(UserStatus.ACTIVE);
        target.setAuthProvider(AuthProvider.LOCAL);
        target.setMustChangePassword(false);
        target = userRepository.save(target);

        // Add a refresh token
        RefreshToken rt = new RefreshToken(
                target.getId(),
                UUID.randomUUID(),
                "some-token-hash-64-characters-long-aaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                Instant.now().plusSeconds(3600),
                "test-agent",
                "127.0.0.1"
        );
        refreshTokenRepository.save(rt);

        HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
        ResponseEntity<ResetPasswordResponse> response = restTemplate.exchange(
                "/api/v1/admin/users/" + target.getId() + "/reset-password",
                HttpMethod.POST,
                entity,
                ResetPasswordResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().temporaryPassword()).hasSize(16);

        User updatedUser = userRepository.findById(target.getId()).orElseThrow();
        assertThat(updatedUser.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches(response.getBody().temporaryPassword(), updatedUser.getPasswordHash())).isTrue();

        // Refresh token should be revoked
        RefreshToken dbRt = refreshTokenRepository.findById(rt.getId()).orElseThrow();
        assertThat(dbRt.getRevokedAt()).isNotNull();

        // Audit outbox event recorded
        List<OutboxEvent> auditEvents = outboxEventRepository.findByEventType("audit.recorded");
        assertThat(auditEvents).isNotEmpty();
        assertThat(auditEvents.get(0).getPayload()).contains("PASSWORD_RESET");
    }
}

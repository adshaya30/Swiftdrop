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
import com.swiftdrop.auth.config.SecurityConfig;
import com.swiftdrop.auth.users.dto.*;
import com.swiftdrop.auth.users.controller.AdminUserController;
import com.swiftdrop.auth.users.service.AdminUserService;
import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.exception.*;
import com.swiftdrop.auth.security.JwtAuthenticationFilter;
import com.swiftdrop.auth.login.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@DisplayName("TST-020: AdminUserController MockMvc Tests")
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminUserService adminUserService;

    @MockBean
    private JwtService jwtService;

    private UUID adminId;
    private UUID targetUserId;
    private AdminUserSummaryResponse sampleUserSummary;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();

        // Admin valid token
        Map<String, Object> adminClaims = new LinkedHashMap<>();
        adminClaims.put("iss", "swiftdrop");
        adminClaims.put("aud", "swiftdrop-api");
        adminClaims.put("typ", "user");
        adminClaims.put("sub", adminId.toString());
        adminClaims.put("role", "ADMIN");
        adminClaims.put("pwc", false);
        adminClaims.put("exp", Instant.now().getEpochSecond() + 3600);
        when(jwtService.parseAndVerifyToken("admin-token")).thenReturn(adminClaims);

        // Customer token
        Map<String, Object> customerClaims = new LinkedHashMap<>(adminClaims);
        customerClaims.put("role", "CUSTOMER");
        when(jwtService.parseAndVerifyToken("customer-token")).thenReturn(customerClaims);

        // Merchant token
        Map<String, Object> merchantClaims = new LinkedHashMap<>(adminClaims);
        merchantClaims.put("role", "MERCHANT");
        when(jwtService.parseAndVerifyToken("merchant-token")).thenReturn(merchantClaims);

        // Courier token
        Map<String, Object> courierClaims = new LinkedHashMap<>(adminClaims);
        courierClaims.put("role", "COURIER");
        when(jwtService.parseAndVerifyToken("courier-token")).thenReturn(courierClaims);

        // Client token
        Map<String, Object> clientClaims = new LinkedHashMap<>(adminClaims);
        clientClaims.put("typ", "client");
        when(jwtService.parseAndVerifyToken("client-token")).thenReturn(clientClaims);

        // Forced password change token (pwc=true)
        Map<String, Object> pwcClaims = new LinkedHashMap<>(adminClaims);
        pwcClaims.put("pwc", true);
        when(jwtService.parseAndVerifyToken("pwc-token")).thenReturn(pwcClaims);

        sampleUserSummary = new AdminUserSummaryResponse(
                targetUserId,
                "Kamal Silva",
                "kamal@swiftdrop.lk",
                "+94771234567",
                UserRole.COURIER,
                UserStatus.ACTIVE,
                AuthProvider.LOCAL,
                false,
                0,
                null,
                Instant.now(),
                Instant.now(),
                null,
                null
        );
    }

    // -------------------------------------------------------------------------
    // Authentication & Authorization Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/admin/users - 401 when Authorization header missing")
    void listUsers_missingToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/admin/users - 403 when role is CUSTOMER")
    void listUsers_customerRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer customer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/users - 403 when role is MERCHANT")
    void listUsers_merchantRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer merchant-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/users - 403 when role is COURIER")
    void listUsers_courierRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer courier-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/users - 403 when token type is client")
    void listUsers_clientToken_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/users - 403 when pwc=true")
    void listUsers_pwcTrue_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer pwc-token"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // List & Detail Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/admin/users - 200 OK with paginated list")
    void listUsers_success() throws Exception {
        Page<AdminUserSummaryResponse> page = new PageImpl<>(Collections.singletonList(sampleUserSummary));
        when(adminUserService.listUsers(any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(targetUserId.toString()))
                .andExpect(jsonPath("$.content[0].email").value("kamal@swiftdrop.lk"))
                .andExpect(jsonPath("$.content[0].role").value("COURIER"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/admin/users/{id} - 200 OK")
    void getUserById_success() throws Exception {
        when(adminUserService.getUser(targetUserId)).thenReturn(sampleUserSummary);

        mockMvc.perform(get("/api/v1/admin/users/" + targetUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetUserId.toString()))
                .andExpect(jsonPath("$.fullName").value("Kamal Silva"));
    }

    // -------------------------------------------------------------------------
    // User Creation Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/admin/users - 201 Created for ADMIN")
    void createUser_admin_success() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "New Admin",
                "newadmin@swiftdrop.lk",
                "+94771234568",
                "ADMIN",
                null,
                null
        );

        AdminUserSummaryResponse adminSummary = new AdminUserSummaryResponse(
                UUID.randomUUID(),
                "New Admin",
                "newadmin@swiftdrop.lk",
                "+94771234568",
                UserRole.ADMIN,
                UserStatus.ACTIVE,
                AuthProvider.LOCAL,
                true,
                0,
                null,
                null,
                Instant.now(),
                null,
                null
        );

        CreateUserResponse response = new CreateUserResponse(
                adminSummary,
                "TempPass12345!aB"
        );

        when(adminUserService.createUser(eq(adminId), any(CreateUserRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.temporaryPassword").value("TempPass12345!aB"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/users - 400 Bad Request when role is CUSTOMER")
    void createUser_customerRole_returns400() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "Customer Name",
                "cust@example.com",
                null,
                "CUSTOMER",
                null,
                null
        );

        when(adminUserService.createUser(eq(adminId), any()))
                .thenThrow(new InvalidUserRoleException("Only ADMIN and COURIER accounts may be created by administrators."));

        mockMvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ROLE"));
    }

    // -------------------------------------------------------------------------
    // Activation & Deactivation Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/admin/users/{id}/activate - 200 OK")
    void activateUser_success() throws Exception {
        when(adminUserService.activateUser(eq(adminId), eq(targetUserId))).thenReturn(sampleUserSummary);

        mockMvc.perform(post("/api/v1/admin/users/" + targetUserId + "/activate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetUserId.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/admin/users/{id}/deactivate - 400 Bad Request when admin deactivates self")
    void deactivateUser_self_returns400() throws Exception {
        when(adminUserService.deactivateUser(eq(adminId), eq(adminId)))
                .thenThrow(new CannotDeactivateSelfException());

        mockMvc.perform(post("/api/v1/admin/users/" + adminId + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_OWN_ACCOUNT"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/users/{id}/deactivate - 409 Conflict when courier has active deliveries")
    void deactivateUser_courierHasActiveDeliveries_returns409() throws Exception {
        when(adminUserService.deactivateUser(eq(adminId), eq(targetUserId)))
                .thenThrow(new CourierHasActiveDeliveriesException(2));

        mockMvc.perform(post("/api/v1/admin/users/" + targetUserId + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURIER_HAS_ACTIVE_DELIVERIES"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/users/{id}/deactivate - 503 Unavailable when delivery-service is down")
    void deactivateUser_deliveryServiceDown_returns503() throws Exception {
        when(adminUserService.deactivateUser(eq(adminId), eq(targetUserId)))
                .thenThrow(new DeliveryServiceUnavailableException("Delivery service unavailable", null));

        mockMvc.perform(post("/api/v1/admin/users/" + targetUserId + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    // -------------------------------------------------------------------------
    // Password Reset Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/admin/users/{id}/reset-password - 200 OK")
    void resetPassword_success() throws Exception {
        ResetPasswordResponse response = new ResetPasswordResponse(
                "NewTempPass1234!",
                "Password reset successfully"
        );

        when(adminUserService.resetPassword(eq(adminId), eq(targetUserId))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/users/" + targetUserId + "/reset-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").value("NewTempPass1234!"));
    }
}

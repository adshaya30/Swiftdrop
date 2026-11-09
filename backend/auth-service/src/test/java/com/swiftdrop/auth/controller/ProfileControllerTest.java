package com.swiftdrop.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.config.SecurityConfig;
import com.swiftdrop.auth.profile.controller.ProfileController;
import com.swiftdrop.auth.profile.dto.ChangePasswordRequest;
import com.swiftdrop.auth.profile.dto.UpdateProfileRequest;
import com.swiftdrop.auth.profile.dto.UserProfileResponse;
import com.swiftdrop.auth.profile.service.ProfileService;
import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.entity.UserRole;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@DisplayName("ProfileController MockMvc Tests")
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProfileService profileService;

    @MockBean
    private JwtService jwtService;

    private UUID userId;
    private UserProfileResponse defaultProfileResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        defaultProfileResponse = new UserProfileResponse(
                userId,
                "Kasun Jayawardena",
                "kasun@example.lk",
                "+94771234567",
                UserRole.CUSTOMER,
                AuthProvider.LOCAL,
                false,
                Instant.now(),
                null
        );

        // Standard valid user token
        Map<String, Object> validClaims = new LinkedHashMap<>();
        validClaims.put("iss", "swiftdrop");
        validClaims.put("aud", "swiftdrop-api");
        validClaims.put("typ", "user");
        validClaims.put("sub", userId.toString());
        validClaims.put("role", "CUSTOMER");
        validClaims.put("pwc", false);
        validClaims.put("exp", Instant.now().getEpochSecond() + 3600);
        when(jwtService.parseAndVerifyToken("valid-token")).thenReturn(validClaims);

        // Token with forced password change (pwc=true)
        Map<String, Object> pwcClaims = new LinkedHashMap<>(validClaims);
        pwcClaims.put("pwc", true);
        when(jwtService.parseAndVerifyToken("pwc-token")).thenReturn(pwcClaims);

        // Client credentials token (typ=client)
        Map<String, Object> clientClaims = new LinkedHashMap<>(validClaims);
        clientClaims.put("typ", "client");
        when(jwtService.parseAndVerifyToken("client-token")).thenReturn(clientClaims);

        // Invalid token
        when(jwtService.parseAndVerifyToken("invalid-token"))
                .thenThrow(new RuntimeException("Signature verification failed"));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/me (FR-AUTH-012)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/me - 401 Unauthorized when Authorization header is missing")
    void getProfile_missingToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/v1/me - 401 Unauthorized when Bearer token is invalid")
    void getProfile_invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/v1/me - 403 Forbidden when client-credentials token (typ=client) is used")
    void getProfile_clientToken_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer client-token"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/v1/me - 200 OK with valid user token")
    void getProfile_validToken_returns200() throws Exception {
        when(profileService.getProfile(userId)).thenReturn(defaultProfileResponse);

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.fullName").value("Kasun Jayawardena"))
                .andExpect(jsonPath("$.email").value("kasun@example.lk"))
                .andExpect(jsonPath("$.phone").value("+94771234567"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.authProvider").value("LOCAL"));
    }

    @Test
    @DisplayName("GET /api/v1/me - 200 OK even when pwc=true (profile inspection is allowed during forced password change)")
    void getProfile_pwcTrue_allowed() throws Exception {
        when(profileService.getProfile(userId)).thenReturn(defaultProfileResponse);

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer pwc-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/me - 404 Not Found when user does not exist in DB")
    void getProfile_userNotFound_returns404() throws Exception {
        when(profileService.getProfile(userId)).thenThrow(new UserNotFoundException(userId));

        mockMvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // -------------------------------------------------------------------------
    // PUT /api/v1/me (FR-AUTH-012, FR-AUTH-014)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PUT /api/v1/me - 200 OK with valid update payload")
    void updateProfile_validPayload_returns200() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Kasun K. Jayawardena", "+94779876543");
        UserProfileResponse updated = new UserProfileResponse(
                userId,
                "Kasun K. Jayawardena",
                "kasun@example.lk",
                "+94779876543",
                UserRole.CUSTOMER,
                AuthProvider.LOCAL,
                false,
                Instant.now(),
                null
        );
        when(profileService.updateProfile(eq(userId), any(UpdateProfileRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Kasun K. Jayawardena"))
                .andExpect(jsonPath("$.phone").value("+94779876543"));
    }

    @Test
    @DisplayName("PUT /api/v1/me - 400 Bad Request when fullName is blank")
    void updateProfile_blankFullName_returns400() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest(" ", "+94779876543");

        mockMvc.perform(put("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PUT /api/v1/me - 400 Bad Request when phone format is invalid")
    void updateProfile_invalidPhone_returns400() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Kasun Jayawardena", "invalid-phone");

        mockMvc.perform(put("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PUT /api/v1/me - 403 Forbidden when pwc=true (FR-AUTH-014 gate blocks profile updates)")
    void updateProfile_pwcTrue_blockedByGate() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Kasun Jayawardena", "+94771234567");

        mockMvc.perform(put("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer pwc-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        verifyNoInteractions(profileService);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/me/change-password (FR-AUTH-013, FR-AUTH-014)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/me/change-password - 204 No Content with clear refresh cookie on success")
    void changePassword_success_returns204AndClearsCookie() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword#123", "NewSecret#2026");
        doNothing().when(profileService).changePassword(eq(userId), any(ChangePasswordRequest.class));

        mockMvc.perform(post("/api/v1/me/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("sd_refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    @Test
    @DisplayName("POST /api/v1/me/change-password - 204 No Content when pwc=true (allowed by gate)")
    void changePassword_pwcTrue_allowedByGate() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword#123", "NewSecret#2026");
        doNothing().when(profileService).changePassword(eq(userId), any(ChangePasswordRequest.class));

        mockMvc.perform(post("/api/v1/me/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer pwc-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/me/change-password - 400 Bad Request when current password is wrong")
    void changePassword_invalidCurrentPassword_returns400() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("Wrong#123", "NewSecret#2026");
        doThrow(new InvalidCurrentPasswordException()).when(profileService).changePassword(eq(userId), any(ChangePasswordRequest.class));

        mockMvc.perform(post("/api/v1/me/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    }

    @Test
    @DisplayName("POST /api/v1/me/change-password - 400 Bad Request when new password same as current")
    void changePassword_samePassword_returns400() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("Current#123", "Current#123");
        doThrow(new PasswordSameAsCurrentException()).when(profileService).changePassword(eq(userId), any(ChangePasswordRequest.class));

        mockMvc.perform(post("/api/v1/me/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NEW_PASSWORD_SAME_AS_CURRENT"));
    }

    @Test
    @DisplayName("POST /api/v1/me/change-password - 400 Bad Request on social/Google account")
    void changePassword_socialAccount_returns400() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("Current#123", "NewSecret#2026");
        doThrow(new SocialAccountPasswordChangeException()).when(profileService).changePassword(eq(userId), any(ChangePasswordRequest.class));

        mockMvc.perform(post("/api/v1/me/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("SOCIAL_ACCOUNT_PASSWORD_NOT_SUPPORTED"));
    }
}

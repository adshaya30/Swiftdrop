package com.swiftdrop.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.config.SecurityConfig;
import com.swiftdrop.auth.dto.request.LoginRequest;
import com.swiftdrop.auth.dto.request.RegistrationRequest;
import com.swiftdrop.auth.dto.response.LoginResponse;
import com.swiftdrop.auth.dto.response.LoginResult;
import com.swiftdrop.auth.dto.response.LoginUserSummary;
import com.swiftdrop.auth.dto.response.UserResponse;
import com.swiftdrop.auth.exception.*;
import com.swiftdrop.auth.service.LoginService;
import com.swiftdrop.auth.service.RegistrationService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("AuthController MockMvc Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegistrationService registrationService;

    @MockBean
    private LoginService loginService;

    @Test
    @DisplayName("POST /api/v1/auth/register - 201 Created with valid payload")
    void register_success_returns201() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                "Kasun Perera",
                "kasun.perera@example.lk",
                "0771234567",
                "SecurePass123!"
        );

        UUID userId = UUID.randomUUID();
        UserResponse mockResponse = new UserResponse(
                userId,
                "Kasun Perera",
                "kasun.perera@example.lk",
                "+94771234567",
                "CUSTOMER",
                "LOCAL",
                "ACTIVE",
                Instant.now()
        );

        when(registrationService.register(any(RegistrationRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.fullName").value("Kasun Perera"))
                .andExpect(jsonPath("$.email").value("kasun.perera@example.lk"))
                .andExpect(jsonPath("$.phone").value("+94771234567"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - 400 Bad Request on blank fullName")
    void register_blankName_returns400() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                "",
                "kasun@example.lk",
                "0771234567",
                "SecurePass123!"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - 400 Bad Request on invalid email")
    void register_invalidEmail_returns400() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                "Kasun Perera",
                "not-an-email",
                "0771234567",
                "SecurePass123!"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - 409 Conflict when email already registered")
    void register_duplicateEmail_returns409() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                "Kasun Perera",
                "kasun@example.lk",
                "0771234567",
                "SecurePass123!"
        );

        when(registrationService.register(any(RegistrationRequest.class)))
                .thenThrow(new EmailAlreadyRegisteredException("kasun@example.lk"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.detail").value("An account with this email address already exists."));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - 400 Bad Request on invalid phone")
    void register_invalidPhone_returns400() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                "Kasun Perera",
                "kasun@example.lk",
                "0000000000",
                "SecurePass123!"
        );

        when(registrationService.register(any(RegistrationRequest.class)))
                .thenThrow(new InvalidPhoneException("0000000000"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("phone"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - 400 Bad Request on weak password")
    void register_weakPassword_returns400() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                "Kasun Perera",
                "kasun@example.lk",
                "0771234567",
                "alllowercasepass"
        );

        when(registrationService.register(any(RegistrationRequest.class)))
                .thenThrow(new PasswordPolicyException("Password must contain at least one uppercase letter"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/auth/login tests (Feature 2)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/auth/login - 200 OK with valid credentials")
    void login_success_returns200AndSetsCookie() throws Exception {
        LoginRequest request = new LoginRequest("kasun.perera@example.lk", "SecurePass123!");
        UUID userId = UUID.randomUUID();

        LoginUserSummary userSummary = new LoginUserSummary(userId, "Kasun Perera", "CUSTOMER", false);
        LoginResponse response = new LoginResponse("test-jwt-access-token", 900, userSummary);
        LoginResult result = new LoginResult(response, "test-raw-refresh-token-value");

        when(loginService.login(any(LoginRequest.class), any(), any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.accessToken").value("test-jwt-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.fullName").value("Kasun Perera"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.mustChangePassword").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - 401 INVALID_CREDENTIALS on bad password")
    void login_invalidCredentials_returns401() throws Exception {
        LoginRequest request = new LoginRequest("kasun@example.lk", "WrongPassword!");

        when(loginService.login(any(LoginRequest.class), any(), any()))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - 429 ACCOUNT_LOCKED on locked account")
    void login_accountLocked_returns429WithRetryAfter() throws Exception {
        LoginRequest request = new LoginRequest("locked@example.lk", "SecurePass123!");

        when(loginService.login(any(LoginRequest.class), any(), any()))
                .thenThrow(new AccountLockedException(900));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "900"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - 403 ACCOUNT_DEACTIVATED on deactivated user")
    void login_deactivatedUser_returns403() throws Exception {
        LoginRequest request = new LoginRequest("deactivated@example.lk", "SecurePass123!");

        when(loginService.login(any(LoginRequest.class), any(), any()))
                .thenThrow(new AccountDeactivatedException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCOUNT_DEACTIVATED"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - 403 MERCHANT_PENDING on pending merchant")
    void login_pendingMerchant_returns403() throws Exception {
        LoginRequest request = new LoginRequest("pending-merchant@example.lk", "SecurePass123!");

        when(loginService.login(any(LoginRequest.class), any(), any()))
                .thenThrow(new MerchantPendingException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("MERCHANT_PENDING"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - 400 Bad Request on blank credentials")
    void login_blankCredentials_returns400() throws Exception {
        LoginRequest request = new LoginRequest("", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}

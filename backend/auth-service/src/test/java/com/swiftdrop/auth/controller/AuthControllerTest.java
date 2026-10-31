package com.swiftdrop.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.config.SecurityConfig;
import com.swiftdrop.auth.dto.request.RegistrationRequest;
import com.swiftdrop.auth.dto.response.UserResponse;
import com.swiftdrop.auth.exception.EmailAlreadyRegisteredException;
import com.swiftdrop.auth.exception.GlobalExceptionHandler;
import com.swiftdrop.auth.exception.InvalidPhoneException;
import com.swiftdrop.auth.exception.PasswordPolicyException;
import com.swiftdrop.auth.service.RegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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
}

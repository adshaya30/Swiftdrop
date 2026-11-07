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
import com.swiftdrop.auth.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Tag;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for POST /api/v1/auth/register.
 * Uses a real PostgreSQL 16 database via Testcontainers.
 * SRS v2.1 – FR-AUTH-001, FR-AUTH-002
 */
@Tag("integration")
@DisplayName("Customer Registration Integration Tests")
class RegistrationControllerIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String ENDPOINT = "/api/v1/auth/register";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Valid registration returns 201 with safe user summary")
    void validRegistration_returns201() {
        ResponseEntity<Map> response = post(validPayload());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("rawtypes")
        Map body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("id")).isNotNull();
        assertThat(body.get("email")).isEqualTo("test@example.com");
        assertThat(body.get("fullName")).isEqualTo("Test Customer");
        assertThat(body.get("role")).isEqualTo("CUSTOMER");
        assertThat(body.get("authProvider")).isEqualTo("LOCAL");
        assertThat(body.get("status")).isEqualTo("ACTIVE");
        // MUST NOT contain password or token
        assertThat(body.containsKey("password")).isFalse();
        assertThat(body.containsKey("passwordHash")).isFalse();
        assertThat(body.containsKey("token")).isFalse();
        assertThat(body.containsKey("accessToken")).isFalse();
        assertThat(body.containsKey("refreshToken")).isFalse();
    }

    @Test
    @DisplayName("Role is always CUSTOMER regardless of client input")
    void roleIsAlwaysCustomer() {
        ResponseEntity<Map> response = post(validPayload());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("role")).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("Auth provider is always LOCAL")
    void authProviderIsLocal() {
        ResponseEntity<Map> response = post(validPayload());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("authProvider")).isEqualTo("LOCAL");
    }

    @Test
    @DisplayName("Email is normalised to lowercase")
    void emailNormalisedToLowercase() {
        Map<String, String> payload = validPayload();
        payload.put("email", "UPPER@EXAMPLE.COM");
        ResponseEntity<Map> response = post(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("email")).isEqualTo("upper@example.com");
    }

    @Test
    @DisplayName("BCrypt hash is stored – not plaintext")
    void bcryptHashStored() {
        post(validPayload());

        com.swiftdrop.auth.domain.User saved = userRepository.findByEmailIgnoreCase("test@example.com").orElseThrow();
        String hash = saved.getPasswordHash();
        assertThat(hash).isNotNull();
        assertThat(hash).startsWith("$2a$");
        assertThat(hash).doesNotContain("Str0ng!Pass");
    }

    // -----------------------------------------------------------------------
    // Duplicate email
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Duplicate email returns 409 with EMAIL_ALREADY_REGISTERED")
    void duplicateEmail_returns409() {
        post(validPayload());
        ResponseEntity<Map> response = post(validPayload());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    @DisplayName("Duplicate email with different case returns 409")
    void duplicateEmailDifferentCase_returns409() {
        post(validPayload()); // test@example.com
        Map<String, String> duplicate = validPayload();
        duplicate.put("email", "TEST@EXAMPLE.COM");
        ResponseEntity<Map> response = post(duplicate);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    // -----------------------------------------------------------------------
    // Validation errors – fullName
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Blank fullName returns 400 VALIDATION_ERROR")
    void blankFullName_returns400() {
        Map<String, String> payload = validPayload();
        payload.put("fullName", "");
        ResponseEntity<Map> response = post(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("SingleChar fullName (< 2 chars) returns 400")
    void tooShortFullName_returns400() {
        Map<String, String> payload = validPayload();
        payload.put("fullName", "A");
        ResponseEntity<Map> response = post(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // -----------------------------------------------------------------------
    // Validation errors – email
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Invalid email format returns 400 VALIDATION_ERROR")
    void invalidEmail_returns400() {
        Map<String, String> payload = validPayload();
        payload.put("email", "not-an-email");
        ResponseEntity<Map> response = post(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    // -----------------------------------------------------------------------
    // Validation errors – phone
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Invalid Sri Lankan phone returns 400")
    void invalidPhone_returns400() {
        Map<String, String> payload = validPayload();
        payload.put("phone", "123456");
        ResponseEntity<Map> response = post(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("UK phone number returns 400")
    void nonLkPhone_returns400() {
        Map<String, String> payload = validPayload();
        payload.put("phone", "+447911123456");
        ResponseEntity<Map> response = post(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Local format 07X phone is accepted and normalised to E.164")
    void localPhoneFormat_accepted() {
        Map<String, String> payload = validPayload();
        payload.put("phone", "0771234567");
        ResponseEntity<Map> response = post(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("phone")).isEqualTo("+94771234567");
    }

    @Test
    @DisplayName("+94 prefix phone is accepted")
    void plus94PhoneFormat_accepted() {
        Map<String, String> payload = validPayload();
        payload.put("phone", "+94771234567");
        ResponseEntity<Map> response = post(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("phone")).isEqualTo("+94771234567");
    }

    // -----------------------------------------------------------------------
    // Validation errors – password
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Short password (< 10 chars) returns 400")
    void shortPassword_returns400() {
        Map<String, String> payload = validPayload();
        payload.put("password", "Short1!");
        ResponseEntity<Map> response = post(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Password without uppercase returns 400")
    void passwordNoUppercase_returns400() {
        Map<String, String> payload = validPayload();
        payload.put("password", "lowercase1!password");
        ResponseEntity<Map> response = post(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Password without special character returns 400")
    void passwordNoSpecial_returns400() {
        Map<String, String> payload = validPayload();
        payload.put("password", "Password1234567");
        ResponseEntity<Map> response = post(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Password containing email local-part returns 400")
    void passwordContainsEmailLocalPart_returns400() {
        Map<String, String> payload = validPayload();
        payload.put("email", "alice@example.com");
        payload.put("password", "Alice!s@pass1"); // contains "alice"
        ResponseEntity<Map> response = post(payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    // -----------------------------------------------------------------------
    // Unknown properties
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Unknown property 'role' in request returns 400")
    void unknownPropertyRole_returns400() throws Exception {
        Map<String, String> payload = validPayload();
        // Add forbidden "role" field
        String json = objectMapper.writeValueAsString(payload).replace("}", ",\"role\":\"ADMIN\"}");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(ENDPOINT, entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_ERROR");
    }

    // -----------------------------------------------------------------------
    // Concurrency: simultaneous duplicate registrations
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Simultaneous duplicate registrations create exactly one user")
    void concurrentDuplicateRegistrations_createOneUser() throws InterruptedException {
        int threads = 5;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger created201 = new AtomicInteger();
        AtomicInteger conflict409 = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    go.await();
                    ResponseEntity<Map> r = post(validPayload());
                    if (r.getStatusCode() == HttpStatus.CREATED) created201.incrementAndGet();
                    else if (r.getStatusCode() == HttpStatus.CONFLICT) conflict409.incrementAndGet();
                } catch (Exception ignored) {
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(created201.get()).isEqualTo(1);
        assertThat(conflict409.get()).isEqualTo(threads - 1);
        assertThat(userRepository.count()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // Public access (no JWT required)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Registration endpoint is publicly accessible without Authorization header")
    void noJwt_canRegister() {
        ResponseEntity<Map> response = post(validPayload());
        // Must NOT be 401 or 403
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Map<String, String> validPayload() {
        Map<String, String> m = new HashMap<>();
        m.put("fullName", "Test Customer");
        m.put("email", "test@example.com");
        m.put("phone", "0771234567");
        m.put("password", "Str0ng!Pass#1");
        return m;
    }

    private ResponseEntity<Map> post(Map<String, String> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);
        return restTemplate.postForEntity(ENDPOINT, entity, Map.class);
    }
}

package com.swiftdrop.auth.merchant.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.auth.AbstractIntegrationTest;
import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.RefreshTokenRepository;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.entity.MerchantStatus;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * POST /api/v1/auth/register-merchant against a real PostgreSQL (Testcontainers).
 * SRS v2.1 - FR-AUTH-016, AT-12 (registration and "pending approval" login).
 */
@Tag("integration")
@Tag("TST-029")
@DisplayName("Merchant registration integration tests")
class MerchantRegistrationIT extends AbstractIntegrationTest {

    private static final String ENDPOINT = "/api/v1/auth/register-merchant";

    @Autowired TestRestTemplate restTemplate;
    @Autowired UserRepository userRepository;
    @Autowired MerchantRepository merchantRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void clean() {
        refreshTokenRepository.deleteAll();
        merchantRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-016: valid registration returns 201, a pending message and no tokens")
    void validRegistration() {
        ResponseEntity<Map> response = post(validPayload());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsOnlyKeys("message");
        assertThat((String) response.getBody().get("message")).contains("waiting for admin approval");
        assertThat(response.getHeaders().containsKey(HttpHeaders.SET_COOKIE)).isFalse();

        User user = userRepository.findByEmailIgnoreCase("shop@example.com").orElseThrow();
        assertThat(user.getRole()).isEqualTo(UserRole.MERCHANT);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getFullName()).isEqualTo("Nimal Perera");
        assertThat(user.getPhone()).isEqualTo("+94771234567");
        assertThat(user.getPasswordHash()).isNotEqualTo("Str0ng!Pass#1").startsWith("$2");

        Merchant merchant = merchantRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.PENDING);
        assertThat(merchant.getBusinessName()).isEqualTo("Lanka Crafts");
        assertThat(merchant.getAddressLine1()).isEqualTo("12 Galle Road");
        assertThat(merchant.getCity()).isEqualTo("Colombo");
        assertThat(merchant.getDiscountPercent()).isEqualByComparingTo("0.00");
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("TST-029 AT-12: a registered merchant cannot log in yet (403 MERCHANT_PENDING)")
    void loginSaysPending() {
        post(validPayload());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> login = restTemplate.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(Map.of("email", "shop@example.com", "password", "Str0ng!Pass#1"), headers), Map.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(login.getBody()).containsEntry("code", "MERCHANT_PENDING");
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-016: duplicate email (any case) gives 409 EMAIL_ALREADY_REGISTERED")
    void duplicateEmail() {
        assertThat(post(validPayload()).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> again = validPayload();
        again.put("email", "SHOP@Example.com");
        ResponseEntity<Map> response = post(again);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "EMAIL_ALREADY_REGISTERED");
        assertThat(merchantRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-016: a customer email is also refused (one account per email)")
    void emailUsedByCustomer() {
        User customer = new User();
        customer.setFullName("A Customer");
        customer.setEmail("shop@example.com");
        customer.setRole(UserRole.CUSTOMER);
        customer.setAuthProvider(com.swiftdrop.auth.entity.AuthProvider.LOCAL);
        customer.setStatus(UserStatus.ACTIVE);
        customer.setPasswordHash("x");
        userRepository.saveAndFlush(customer);

        assertThat(post(validPayload()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(merchantRepository.count()).isZero();
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-016: field limits and missing fields give 400 VALIDATION_ERROR with errors[]")
    void validation() {
        Map<String, Object> bad = validPayload();
        bad.put("businessName", "A");
        bad.put("contactName", "");
        bad.put("addressLine1", "x".repeat(151));
        bad.put("city", "y".repeat(81));
        bad.put("businessRegistrationNo", "z".repeat(41));
        bad.put("email", "not-an-email");
        bad.remove("phone");

        ResponseEntity<Map> response = post(bad);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> errors = (List<Map<String, String>>) response.getBody().get("errors");
        assertThat(errors).extracting(e -> e.get("field")).contains("businessName", "contactName", "addressLine1",
                "city", "businessRegistrationNo", "email", "phone");
        assertThat(merchantRepository.count()).isZero();
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-016: invalid phone and weak password give 400 on their own fields")
    void phoneAndPassword() {
        Map<String, Object> badPhone = validPayload();
        badPhone.put("phone", "12345");
        assertThat(post(badPhone).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> weak = validPayload();
        weak.put("password", "alllowercase1!");
        ResponseEntity<Map> response = post(weak);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-016: unknown properties (for example role or status) are rejected with 400")
    void unknownProperty() {
        Map<String, Object> payload = validPayload();
        payload.put("role", "ADMIN");
        assertThat(post(payload).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> status = validPayload();
        status.put("status", "APPROVED");
        assertThat(post(status).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("TST-033 FR-AUTH-016: the endpoint is public (no token needed)")
    void isPublic() {
        assertThat(post(validPayload()).getStatusCode()).isNotIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> m = new HashMap<>();
        m.put("businessName", "Lanka Crafts");
        m.put("contactName", "Nimal Perera");
        m.put("email", "shop@example.com");
        m.put("phone", "0771234567");
        m.put("password", "Str0ng!Pass#1");
        m.put("addressLine1", "12 Galle Road");
        m.put("city", "Colombo");
        return m;
    }

    private ResponseEntity<Map> post(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(ENDPOINT, new HttpEntity<>(payload, headers), Map.class);
    }
}

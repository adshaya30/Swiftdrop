package com.swiftdrop.auth.merchant.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.auth.AbstractIntegrationTest;
import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.RefreshTokenRepository;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.entity.MerchantStatus;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.login.service.JwtService;
import com.swiftdrop.auth.merchant.dto.InternalMerchantView;
import com.swiftdrop.auth.merchant.dto.InternalUserContact;
import com.swiftdrop.auth.merchant.service.InternalLookupService;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.security.InternalApiKey;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pending-merchants counter and internal lookups against a real PostgreSQL.
 * SRS v2.1 - FR-AUTH-022, FR-AUTH-024, TST-033.
 */
@Tag("integration")
@Tag("TST-033")
@DisplayName("Merchant dashboard counter and internal lookups integration tests")
class MerchantDashboardAndInternalIT extends AbstractIntegrationTest {

    private static final String DASHBOARD = "/api/v1/admin/dashboard/merchants";

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository userRepository;
    @Autowired MerchantRepository merchantRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;
    @Autowired InternalLookupService lookupService;
    @Autowired InternalApiKey internalApiKey;
    @Autowired List<FilterRegistrationBean<?>> filterRegistrations;

    User admin;
    String adminToken;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM outbox_events");
        refreshTokenRepository.deleteAll();
        merchantRepository.deleteAll();
        userRepository.deleteAll();
        admin = createUser("admin@swiftdrop.lk", UserRole.ADMIN);
        adminToken = jwtService.issueAccessToken(admin.getId(), "ADMIN", false, null);
    }

    // ------------------------------------------------------------------ FR-AUTH-024

    @Test
    @DisplayName("FR-AUTH-024: the dashboard counter returns {pendingMerchants} and follows status changes")
    void pendingCounter() {
        assertThat(pending()).isZero();

        Merchant a = createMerchant("a1@example.com", MerchantStatus.PENDING);
        createMerchant("a2@example.com", MerchantStatus.PENDING);
        createMerchant("a3@example.com", MerchantStatus.APPROVED);
        createMerchant("a4@example.com", MerchantStatus.REJECTED);
        createMerchant("a5@example.com", MerchantStatus.SUSPENDED);
        assertThat(pending()).isEqualTo(2);

        ResponseEntity<Map> approved = call(HttpMethod.POST, "/api/v1/admin/merchants/" + a.getId() + "/approve", adminToken);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pending()).isEqualTo(1);

        ResponseEntity<Map> response = call(HttpMethod.GET, DASHBOARD, adminToken);
        assertThat(response.getBody()).containsOnlyKeys("pendingMerchants");
    }

    @Test
    @DisplayName("TST-033 FR-AUTH-024: no token gives 401; CUSTOMER and MERCHANT tokens give 403")
    void dashboardAuthorisation() {
        assertThat(call(HttpMethod.GET, DASHBOARD, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        User customer = createUser("customer@example.com", UserRole.CUSTOMER);
        User merchantUser = createUser("merchant@example.com", UserRole.MERCHANT);
        for (User other : List.of(customer, merchantUser)) {
            String token = jwtService.issueAccessToken(other.getId(), other.getRole().name(), false, null);
            assertThat(call(HttpMethod.GET, DASHBOARD, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ------------------------------------------------------------------ FR-AUTH-022

    @Test
    @DisplayName("FR-AUTH-022: user contact and merchant view are read from the database; unknown ids give 404")
    void lookupsAgainstDatabase() {
        Merchant merchant = createMerchant("shop@example.com", MerchantStatus.APPROVED);
        merchant.setDiscountPercent(new BigDecimal("12.50"));
        merchant = merchantRepository.save(merchant);
        User user = userRepository.findById(merchant.getUserId()).orElseThrow();

        InternalUserContact contact = lookupService.contact(user.getId().toString());
        assertThat(contact.id()).isEqualTo(user.getId());
        assertThat(contact.fullName()).isEqualTo("Nimal Perera");
        assertThat(contact.email()).isEqualTo("shop@example.com");
        assertThat(contact.phone()).isEqualTo("+94771234567");

        InternalMerchantView view = lookupService.merchant(merchant.getId().toString());
        assertThat(view.id()).isEqualTo(merchant.getId());
        assertThat(view.userId()).isEqualTo(user.getId());
        assertThat(view.status()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(view.userStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(view.discountPercent()).isEqualByComparingTo("12.50");
        assertThat(view.contactEmail()).isEqualTo("shop@example.com");

        user.setStatus(UserStatus.DEACTIVATED);
        userRepository.save(user);
        assertThat(lookupService.merchant(merchant.getId().toString()).userStatus()).isEqualTo(UserStatus.DEACTIVATED);

        for (Runnable lookup : List.<Runnable>of(
                () -> lookupService.contact(UUID.randomUUID().toString()),
                () -> lookupService.merchant(UUID.randomUUID().toString()),
                () -> lookupService.contact("nope"),
                () -> lookupService.merchant("nope"))) {
            try {
                lookup.run();
                throw new AssertionError("expected NOT_FOUND");
            } catch (DomainException e) {
                assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND);
            }
        }
    }

    @Test
    @DisplayName("TST-033 FR-AUTH-022: the shared X-Internal-Key filter is registered for exactly the two internal paths")
    void internalKeyFilterIsWired() {
        assertThat(internalApiKey.value()).hasSizeGreaterThanOrEqualTo(32);
        List<String> patterns = filterRegistrations.stream()
                .filter(r -> r.getFilter() instanceof com.swiftdrop.common.security.InternalKeyFilter)
                .flatMap(r -> r.getUrlPatterns().stream()).toList();
        assertThat(patterns).containsExactlyInAnyOrder("/internal/v1/users/*", "/internal/v1/merchants/*");
    }

    @Test
    @DisplayName("TST-033 FR-AUTH-022: internal endpoints require valid X-Internal-Key (401 without, 403 invalid, 200 with valid key)")
    void internalPathsAreClosedByDefault() {
        User user = createUser("someone@example.com", UserRole.CUSTOMER);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Key", internalApiKey.value());
        ResponseEntity<String> response = rest.exchange("/internal/v1/users/" + user.getId() + "/contact",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(user.getId().toString());
        assertThat(response.getBody()).contains("someone@example.com");

        ResponseEntity<String> noKey = rest.exchange("/internal/v1/users/" + user.getId() + "/contact",
                HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
        assertThat(noKey.getStatusCode().value()).isIn(401, 403);
        assertThat(String.valueOf(noKey.getBody())).doesNotContain("someone@example.com");

        HttpHeaders badHeaders = new HttpHeaders();
        badHeaders.set("X-Internal-Key", "invalid-key-012345678901234567890123");
        ResponseEntity<String> badKey = rest.exchange("/internal/v1/users/" + user.getId() + "/contact",
                HttpMethod.GET, new HttpEntity<>(badHeaders), String.class);
        assertThat(badKey.getStatusCode().value()).isIn(401, 403);
        assertThat(String.valueOf(badKey.getBody())).doesNotContain("someone@example.com");
    }

    // ------------------------------------------------------------------ helpers

    private long pending() {
        Object value = call(HttpMethod.GET, DASHBOARD, adminToken).getBody().get("pendingMerchants");
        return ((Number) value).longValue();
    }

    private User createUser(String email, UserRole role) {
        User user = new User();
        user.setFullName("Nimal Perera");
        user.setEmail(email);
        user.setPhone("+94771234567");
        user.setPasswordHash("x");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthProvider(AuthProvider.LOCAL);
        return userRepository.save(user);
    }

    private Merchant createMerchant(String email, MerchantStatus status) {
        User user = createUser(email, UserRole.MERCHANT);
        Merchant merchant = new Merchant(user.getId(), "Lanka Crafts " + email, status);
        merchant.setAddressLine1("12 Galle Road");
        merchant.setCity("Colombo");
        return merchantRepository.save(merchant);
    }

    private ResponseEntity<Map> call(HttpMethod method, String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, method, new HttpEntity<>(headers), Map.class);
    }
}

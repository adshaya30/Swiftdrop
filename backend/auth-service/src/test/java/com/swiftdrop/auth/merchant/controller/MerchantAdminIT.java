package com.swiftdrop.auth.merchant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

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
import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.events.AuditEvents;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Merchant administration against a real PostgreSQL (Testcontainers).
 * SRS v2.1 - FR-AUTH-017, FR-AUTH-023, BR-12, BR-23, AT-12; TST-029 (merchant lifecycle), TST-033 (401/403).
 */
@Tag("integration")
@Tag("TST-029")
@DisplayName("Admin merchants integration tests")
class MerchantAdminIT extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/admin/merchants";
    private static final String PASSWORD = "Str0ng!Pass#1";
    private static final String REASON = "Documents could not be verified";

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository userRepository;
    @Autowired MerchantRepository merchantRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean AuditEvents auditEvents;

    String adminToken;
    UUID adminId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM outbox_events");
        refreshTokenRepository.deleteAll();
        merchantRepository.deleteAll();
        userRepository.deleteAll();

        User admin = new User();
        admin.setFullName("Super Admin");
        admin.setEmail("admin@swiftdrop.lk");
        admin.setPhone("+94770000001");
        admin.setPasswordHash("x");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setAuthProvider(AuthProvider.LOCAL);
        admin = userRepository.save(admin);
        adminId = admin.getId();
        adminToken = jwtService.issueAccessToken(adminId, "ADMIN", false, null);
    }

    // ------------------------------------------------------------------ authorisation (TST-033)

    @Test
    @DisplayName("TST-033 FR-AUTH-017: no token gives 401 on every endpoint")
    void unauthenticated() {
        UUID id = UUID.randomUUID();
        assertThat(call(HttpMethod.GET, BASE, null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET, BASE + "/" + id, null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        for (String action : List.of("approve", "reject", "suspend", "reinstate")) {
            assertThat(call(HttpMethod.POST, BASE + "/" + id + "/" + action, Map.of("reason", REASON), null)
                    .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(call(HttpMethod.PATCH, BASE + "/" + id + "/discount", Map.of("discountPercent", 10), null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("TST-033 FR-AUTH-017: CUSTOMER and MERCHANT tokens get 403 and change nothing")
    void forbiddenForOtherRoles() {
        UUID merchantId = registerMerchant("Lanka Crafts", "shop@example.com");
        User merchantUser = userRepository.findByEmailIgnoreCase("shop@example.com").orElseThrow();
        User customer = new User();
        customer.setFullName("A Customer");
        customer.setEmail("customer@example.com");
        customer.setPasswordHash("x");
        customer.setRole(UserRole.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);
        customer.setAuthProvider(AuthProvider.LOCAL);
        customer = userRepository.save(customer);

        List<String> tokens = List.of(
                jwtService.issueAccessToken(customer.getId(), "CUSTOMER", false, null),
                jwtService.issueAccessToken(merchantUser.getId(), "MERCHANT", false, merchantId));
        for (String token : tokens) {
            assertThat(call(HttpMethod.GET, BASE, null, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(call(HttpMethod.GET, BASE + "/" + merchantId, null, token).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(call(HttpMethod.POST, BASE + "/" + merchantId + "/approve", null, token).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(call(HttpMethod.PATCH, BASE + "/" + merchantId + "/discount", Map.of("discountPercent", 10), token)
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(merchantRepository.findById(merchantId).orElseThrow().getStatus()).isEqualTo(MerchantStatus.PENDING);
    }

    // ------------------------------------------------------------------ list and detail

    @Test
    @DisplayName("TST-029 FR-AUTH-017: list filters by status and name prefix (case-insensitive), paginated and sorted")
    void listAndFilter() {
        UUID a = registerMerchant("Alpha Traders", "zq1@example.com");
        registerMerchant("alpine Goods", "zq2@example.com");
        registerMerchant("Beta Stores", "zq3@example.com");
        approve(a);

        Map<String, Object> all = body(call(HttpMethod.GET, BASE + "?sort=businessName,asc", null, adminToken));
        assertThat(all).containsEntry("totalItems", 3).containsEntry("page", 0).containsEntry("size", 20)
                .containsEntry("totalPages", 1);
        assertThat(names(all)).containsExactly("Alpha Traders", "alpine Goods", "Beta Stores");

        assertThat(names(body(call(HttpMethod.GET, BASE + "?status=PENDING&sort=businessName,asc", null, adminToken))))
                .containsExactly("alpine Goods", "Beta Stores");
        assertThat(names(body(call(HttpMethod.GET, BASE + "?q=AL&sort=businessName,asc", null, adminToken))))
                .containsExactly("Alpha Traders", "alpine Goods");
        assertThat(names(body(call(HttpMethod.GET, BASE + "?q=al&status=APPROVED", null, adminToken))))
                .containsExactly("Alpha Traders");

        Map<String, Object> page = body(call(HttpMethod.GET, BASE + "?size=2&page=1&sort=businessName,asc", null, adminToken));
        assertThat(page).containsEntry("totalItems", 3).containsEntry("totalPages", 2).containsEntry("size", 2);
        assertThat(names(page)).containsExactly("Beta Stores");
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-017: % and _ in q are literal characters, not wildcards")
    void likeCharactersAreEscaped() {
        registerMerchant("A_B Trading", "zq1@example.com");
        registerMerchant("AXB Trading", "zq2@example.com");
        assertThat(names(body(call(HttpMethod.GET, BASE + "?q=A_", null, adminToken)))).containsExactly("A_B Trading");
        assertThat(names(body(call(HttpMethod.GET, BASE + "?q=%25", null, adminToken)))).isEmpty();
    }

    @Test
    @DisplayName("TST-029 BR-26: bad page, size, status filter or sort field give 400 VALIDATION_ERROR")
    void listValidation() {
        for (String query : List.of("?size=101", "?size=0", "?page=-1", "?status=NOPE", "?sort=passwordHash,asc",
                "?sort=createdAt,sideways")) {
            ResponseEntity<Map> response = call(HttpMethod.GET, BASE + query, null, adminToken);
            assertThat(response.getStatusCode()).as(query).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).as(query).containsEntry("code", "VALIDATION_ERROR");
        }
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-017: detail has merchant fields and contact data; unknown id gives 404")
    void detail() {
        UUID id = registerMerchant("Lanka Crafts", "shop@example.com");

        Map<String, Object> detail = body(call(HttpMethod.GET, BASE + "/" + id, null, adminToken));
        assertThat(detail).containsEntry("id", id.toString()).containsEntry("businessName", "Lanka Crafts")
                .containsEntry("contactName", "Nimal Perera").containsEntry("email", "shop@example.com")
                .containsEntry("phone", "+94771234567").containsEntry("addressLine1", "12 Galle Road")
                .containsEntry("city", "Colombo").containsEntry("status", "PENDING");
        assertThat(detail).containsKeys("statusReason", "discountPercent", "decidedAt", "decidedBy", "createdAt");
        assertThat(detail).doesNotContainKeys("passwordHash", "password");

        ResponseEntity<Map> missing = call(HttpMethod.GET, BASE + "/" + UUID.randomUUID(), null, adminToken);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).containsEntry("code", "NOT_FOUND");
    }

    // ------------------------------------------------------------------ lifecycle (AT-12)

    @Test
    @DisplayName("TST-029 AT-12: pending login blocked, approve opens login, suspend blocks it and revokes tokens, reinstate restores")
    void approveSuspendReinstateFlow() {
        UUID id = registerMerchant("Lanka Crafts", "shop@example.com");
        assertThat(login("shop@example.com")).extracting(ResponseEntity::getStatusCode).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(login("shop@example.com").getBody()).containsEntry("code", "MERCHANT_PENDING");

        Map<String, Object> approved = body(post(id, "approve", null));
        assertThat(approved).containsEntry("status", "APPROVED").containsEntry("decidedBy", adminId.toString());
        ResponseEntity<Map> ok = login("shop@example.com");
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID merchantUserId = userRepository.findByEmailIgnoreCase("shop@example.com").orElseThrow().getId();
        assertThat(activeRefreshTokens(merchantUserId)).isEqualTo(1);

        // BR-12: APPROVED cannot be approved or rejected again
        assertThat(post(id, "approve", null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ResponseEntity<Map> badReject = post(id, "reject", Map.of("reason", REASON));
        assertThat(badReject.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(badReject.getBody()).containsEntry("code", "INVALID_STATUS_TRANSITION");

        Map<String, Object> suspended = body(post(id, "suspend", Map.of("reason", REASON)));
        assertThat(suspended).containsEntry("status", "SUSPENDED").containsEntry("statusReason", REASON);
        assertThat(activeRefreshTokens(merchantUserId)).isZero(); // BR-23
        assertThat(login("shop@example.com").getBody()).containsEntry("code", "MERCHANT_SUSPENDED");

        assertThat(post(id, "suspend", Map.of("reason", REASON)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<String, Object> reinstated = body(post(id, "reinstate", null));
        assertThat(reinstated).containsEntry("status", "APPROVED").containsEntry("statusReason", null);
        assertThat(login("shop@example.com").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post(id, "reinstate", null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("TST-029 AT-12: rejection with a reason blocks login; re-approval restores it")
    void rejectThenReapprove() {
        UUID id = registerMerchant("Lanka Crafts", "shop@example.com");

        assertThat(post(id, "reject", Map.of("reason", "short")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(id, "reject", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(id, "reject", Map.of("reason", "x".repeat(301))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(id, "reject", Map.of("reason", REASON, "extra", 1)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(body(post(id, "reject", Map.of("reason", REASON)))).containsEntry("status", "REJECTED");
        assertThat(login("shop@example.com").getBody()).containsEntry("code", "MERCHANT_REJECTED");
        // REJECTED can only be approved
        assertThat(post(id, "reject", Map.of("reason", REASON)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post(id, "suspend", Map.of("reason", REASON)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post(id, "reinstate", null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(body(post(id, "approve", null))).containsEntry("status", "APPROVED").containsEntry("statusReason", null);
        assertThat(login("shop@example.com").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("TST-029 BR-12: PENDING cannot be suspended or reinstated")
    void pendingCannotSuspendOrReinstate() {
        UUID id = registerMerchant("Lanka Crafts", "shop@example.com");
        assertThat(post(id, "suspend", Map.of("reason", REASON)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post(id, "reinstate", null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(merchantRepository.findById(id).orElseThrow().getStatus()).isEqualTo(MerchantStatus.PENDING);
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-017: a status action on an unknown merchant gives 404")
    void unknownMerchant() {
        UUID id = UUID.randomUUID();
        assertThat(post(id, "approve", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post(id, "reject", Map.of("reason", REASON)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.PATCH, BASE + "/" + id + "/discount", Map.of("discountPercent", 10), adminToken)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------ discount

    @Test
    @DisplayName("TST-029 FR-AUTH-017: discount 0-50 with two decimals is stored and audited; others give 400")
    void discount() {
        UUID id = registerMerchant("Lanka Crafts", "shop@example.com");
        approve(id);

        ResponseEntity<Map> ok = call(HttpMethod.PATCH, BASE + "/" + id + "/discount",
                Map.of("discountPercent", 12.5), adminToken);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(merchantRepository.findById(id).orElseThrow().getDiscountPercent()).isEqualByComparingTo("12.50");
        assertThat(auditRows("MERCHANT_DISCOUNT_CHANGED")).hasSize(1);
        assertThat(auditRows("MERCHANT_DISCOUNT_CHANGED").get(0)).contains("12.50");

        for (Object bad : List.of(50.01, -1, 12.345, "abc")) {
            ResponseEntity<Map> response = call(HttpMethod.PATCH, BASE + "/" + id + "/discount",
                    Map.of("discountPercent", bad), adminToken);
            assertThat(response.getStatusCode()).as(String.valueOf(bad)).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(call(HttpMethod.PATCH, BASE + "/" + id + "/discount", Map.of(), adminToken).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.PATCH, BASE + "/" + id + "/discount", Map.of("discountPercent", 50), adminToken)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(merchantRepository.findById(id).orElseThrow().getDiscountPercent()).isEqualByComparingTo("50.00");
    }

    // ------------------------------------------------------------------ events and audit (FR-AUTH-023)

    @Test
    @DisplayName("TST-029 FR-AUTH-017/023: each status action writes one merchant.status.changed and one audit.recorded row")
    void outboxRows() {
        UUID id = registerMerchant("Lanka Crafts", "shop@example.com");
        post(id, "reject", Map.of("reason", REASON));
        post(id, "approve", null);
        post(id, "suspend", Map.of("reason", REASON));
        post(id, "reinstate", null);

        List<String> events = jdbc.queryForList(
                "SELECT payload::text FROM outbox_events WHERE routing_key = 'merchant.status.changed' ORDER BY created_at",
                String.class);
        assertThat(events).hasSize(4);
        assertThat(events.get(0)).contains("\"previousStatus\": \"PENDING\"", "\"status\": \"REJECTED\"")
                .contains("\"reason\": \"" + REASON + "\"").contains("\"businessName\": \"Lanka Crafts\"")
                .contains("\"merchantId\": \"" + id + "\"").contains("\"schemaVersion\": 1");
        assertThat(events.get(1)).contains("\"previousStatus\": \"REJECTED\"", "\"status\": \"APPROVED\"");
        assertThat(events.get(2)).contains("\"previousStatus\": \"APPROVED\"", "\"status\": \"SUSPENDED\"");
        assertThat(events.get(3)).contains("\"previousStatus\": \"SUSPENDED\"", "\"status\": \"APPROVED\"");
        // BR-28: no contact data in events
        assertThat(String.join("", events)).doesNotContain("shop@example.com", "+94771234567", "Galle");

        for (String action : List.of("MERCHANT_REJECTED", "MERCHANT_APPROVED", "MERCHANT_SUSPENDED", "MERCHANT_REINSTATED")) {
            List<String> rows = auditRows(action);
            assertThat(rows).as(action).hasSize(1);
            assertThat(rows.get(0)).contains("\"actorUserId\": \"" + adminId + "\"", "\"actorRole\": \"ADMIN\"",
                    "\"entityType\": \"MERCHANT\"", "\"entityId\": \"" + id + "\"", "\"service\": \"auth-service");
            // FR-AUTH-023: details never contain reasons, secrets or contact data
            assertThat(rows.get(0)).doesNotContain(REASON, "shop@example.com", "+94771234567", "password");
        }
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-017: a failure while writing the audit event rolls the whole status change back")
    void statusChangeAndEventsAreOneTransaction() {
        UUID id = registerMerchant("Lanka Crafts", "shop@example.com");
        doThrow(new IllegalStateException("simulated failure")).when(auditEvents)
                .record(any(UUID.class), any(ActorRole.class), eq("MERCHANT_APPROVED"), any(), any(), any());

        ResponseEntity<Map> response = post(id, "approve", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        Merchant merchant = merchantRepository.findById(id).orElseThrow();
        assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.PENDING);
        assertThat(merchant.getDecidedBy()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE routing_key = 'merchant.status.changed'",
                Integer.class)).isZero();
    }

    // ------------------------------------------------------------------ concurrency

    @Test
    @DisplayName("TST-029 BR-12: two admins approving at the same time: one 200, one 409, one event")
    void concurrentApproveIsSerialised() throws Exception {
        UUID id = registerMerchant("Lanka Crafts", "shop@example.com");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<HttpStatus>> results = List.of(
                pool.submit(() -> { go.await(); return HttpStatus.valueOf(post(id, "approve", null).getStatusCode().value()); }),
                pool.submit(() -> { go.await(); return HttpStatus.valueOf(post(id, "approve", null).getStatusCode().value()); }));
        go.countDown();
        List<HttpStatus> statuses = List.of(results.get(0).get(), results.get(1).get());
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE routing_key = 'merchant.status.changed'",
                Integer.class)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private UUID registerMerchant(String businessName, String email) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("businessName", businessName);
        payload.put("contactName", "Nimal Perera");
        payload.put("email", email);
        payload.put("phone", "0771234567");
        payload.put("password", PASSWORD);
        payload.put("addressLine1", "12 Galle Road");
        payload.put("city", "Colombo");
        ResponseEntity<Map> response = call(HttpMethod.POST, "/api/v1/auth/register-merchant", payload, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        return merchantRepository.findByUserId(user.getId()).orElseThrow().getId();
    }

    private void approve(UUID id) {
        assertThat(post(id, "approve", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map> post(UUID id, String action, Object body) {
        return call(HttpMethod.POST, BASE + "/" + id + "/" + action, body, adminToken);
    }

    private ResponseEntity<Map> login(String email) {
        return call(HttpMethod.POST, "/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), null);
    }

    private ResponseEntity<Map> call(HttpMethod method, String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode().is2xxSuccessful()).as(String.valueOf(response.getBody())).isTrue();
        return (Map<String, Object>) response.getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<String> names(Map<String, Object> page) {
        return ((List<Map<String, Object>>) page.get("items")).stream().map(i -> (String) i.get("businessName")).toList();
    }

    private long activeRefreshTokens(UUID userId) {
        return refreshTokenRepository.findAll().stream()
                .filter(t -> userId.equals(t.getUserId()) && t.getRevokedAt() == null).count();
    }

    private List<String> auditRows(String action) {
        return jdbc.queryForList("SELECT payload::text FROM outbox_events WHERE routing_key = 'audit.recorded' "
                + "AND payload->'data'->>'action' = ?", String.class, action);
    }
}

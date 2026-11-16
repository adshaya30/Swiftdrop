package com.swiftdrop.auth.merchant.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.swiftdrop.auth.merchant.service.CredentialSecrets;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * API credentials and the token endpoint against a real PostgreSQL (Testcontainers).
 * SRS v2.1 - FR-AUTH-018, FR-AUTH-019, AT-13, TST-029, TST-032 (auth part), TST-033, BR-12, BR-23.
 */
@Tag("integration")
@Tag("TST-032")
@DisplayName("Merchant credentials and token endpoint integration tests")
class MerchantCredentialsIT extends AbstractIntegrationTest {

    private static final String CREDENTIALS = "/api/v1/merchant/credentials";
    private static final String TOKEN = "/api/v1/oauth/token";

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository userRepository;
    @Autowired MerchantRepository merchantRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    User merchantUser;
    Merchant merchant;
    String dashboardToken;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM merchant_credentials");
        refreshTokenRepository.deleteAll();
        merchantRepository.deleteAll();
        userRepository.deleteAll();

        merchantUser = createUser("shop@example.com", UserRole.MERCHANT);
        merchant = createMerchant(merchantUser, MerchantStatus.APPROVED);
        dashboardToken = jwtService.issueUserToken(merchantUser, merchant);
    }

    // ------------------------------------------------------------------ AT-13

    @Test
    @DisplayName("TST-032 AT-13: create credentials (secret shown once), obtain a token, wrong secret gives invalid_client")
    void createThenObtainToken() throws Exception {
        ResponseEntity<Map> created = call(HttpMethod.POST, CREDENTIALS, Map.of("label", "production"), dashboardToken);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getCacheControl()).contains("no-store");
        String clientId = (String) created.getBody().get("clientId");
        String secret = (String) created.getBody().get("clientSecret");
        assertThat(clientId).matches("^sdc_[A-Za-z0-9]{24}$");
        assertThat(secret).matches("^sds_[A-Za-z0-9_-]{43}$");
        assertThat(created.getBody()).containsEntry("label", "production").containsEntry("status", "ACTIVE");

        // the secret is never shown again and only its hash is stored
        String listing = call(HttpMethod.GET, CREDENTIALS, null, dashboardToken).getBody().toString();
        assertThat(listing).contains(clientId).doesNotContain(secret).doesNotContain("clientSecret")
                .doesNotContain("Hash");
        assertThat(jdbc.queryForObject("SELECT client_secret_hash FROM merchant_credentials WHERE client_id = ?",
                String.class, clientId)).isEqualTo(CredentialSecrets.sha256Hex(secret));

        ResponseEntity<Map> token = tokenRequest(form("client_credentials", clientId, secret, null), null);
        assertThat(token.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(token.getHeaders().getCacheControl()).contains("no-store");
        assertThat(token.getBody()).containsEntry("token_type", "Bearer").containsEntry("expires_in", 900)
                .containsEntry("scope", "orders:read orders:write");
        Map<String, Object> claims = claims((String) token.getBody().get("access_token"));
        assertThat(claims).containsEntry("typ", "client").containsEntry("role", "MERCHANT")
                .containsEntry("sub", merchantUser.getId().toString()).containsEntry("mid", merchant.getId().toString())
                .containsEntry("scope", "orders:read orders:write").containsEntry("iss", "swiftdrop")
                .containsEntry("aud", "swiftdrop-api");
        assertThat(jdbc.queryForObject("SELECT last_used_at FROM merchant_credentials WHERE client_id = ?",
                java.sql.Timestamp.class, clientId)).isNotNull();

        ResponseEntity<Map> wrong = tokenRequest(form("client_credentials", clientId, secret + "x", null), null);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getBody()).containsEntry("error", "invalid_client").containsKey("error_description");
        assertThat(wrong.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Basic");
    }

    @Test
    @DisplayName("TST-029 AT-13: a third ACTIVE credential is refused; revoking one makes room again")
    void limitOfTwo() {
        create("one");
        Map<String, Object> second = create("two");
        ResponseEntity<Map> third = call(HttpMethod.POST, CREDENTIALS, Map.of("label", "three"), dashboardToken);
        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(third.getBody()).containsEntry("code", "CREDENTIAL_LIMIT_REACHED");

        assertThat(call(HttpMethod.DELETE, CREDENTIALS + "/" + second.get("id"), null, dashboardToken).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(HttpMethod.POST, CREDENTIALS, Map.of("label", "three"), dashboardToken).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("TST-013 FR-AUTH-018: four parallel creates produce exactly two credentials (race-safe limit)")
    void concurrentCreates() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String label = "key" + i;
            results.add(pool.submit(() -> {
                go.await();
                return call(HttpMethod.POST, CREDENTIALS, Map.of("label", label), dashboardToken).getStatusCode().value();
            }));
        }
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(201, 201, 409, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM merchant_credentials WHERE status = 'ACTIVE'", Integer.class))
                .isEqualTo(2);
    }

    // ------------------------------------------------------------------ revoke and list

    @Test
    @DisplayName("TST-032 BR-25: revoke is idempotent, another merchant's or unknown credential gives 404, revoked cannot get tokens")
    void revoke() {
        Map<String, Object> credential = create("one");
        String id = (String) credential.get("id");

        assertThat(call(HttpMethod.DELETE, CREDENTIALS + "/" + id, null, dashboardToken).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(HttpMethod.DELETE, CREDENTIALS + "/" + id, null, dashboardToken).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(HttpMethod.DELETE, CREDENTIALS + "/" + UUID.randomUUID(), null, dashboardToken).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> token = tokenRequest(form("client_credentials", (String) credential.get("clientId"),
                (String) credential.get("clientSecret"), null), null);
        assertThat(token.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(token.getBody()).containsEntry("error", "invalid_client");

        // a second merchant cannot revoke or see the first one's credential
        User otherUser = createUser("other@example.com", UserRole.MERCHANT);
        Merchant other = createMerchant(otherUser, MerchantStatus.APPROVED);
        String otherToken = jwtService.issueUserToken(otherUser, other);
        Map<String, Object> mine = create("two");
        ResponseEntity<Map> foreign = call(HttpMethod.DELETE, CREDENTIALS + "/" + mine.get("id"), null, otherToken);
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(foreign.getBody()).containsEntry("code", "NOT_FOUND");
        assertThat(call(HttpMethod.GET, CREDENTIALS, null, otherToken).getBody().get("totalItems")).isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT status FROM merchant_credentials WHERE id = ?::uuid", String.class,
                (String) mine.get("id"))).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-018: list shows id, clientId, label, status, createdAt, lastUsedAt, newest first, paginated")
    void list() {
        create("one");
        create("two");

        Map<String, Object> page = call(HttpMethod.GET, CREDENTIALS, null, dashboardToken).getBody();
        assertThat(page).containsEntry("totalItems", 2).containsEntry("page", 0).containsEntry("size", 20);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        assertThat(items).extracting(i -> i.get("label")).containsExactly("two", "one");
        assertThat(items.get(0)).containsKeys("id", "clientId", "label", "status", "createdAt", "lastUsedAt");
        assertThat(items.get(0)).doesNotContainKeys("clientSecret", "clientSecretHash");

        assertThat(call(HttpMethod.GET, CREDENTIALS + "?size=1&page=1", null, dashboardToken).getBody())
                .containsEntry("totalPages", 2);
        assertThat(call(HttpMethod.GET, CREDENTIALS + "?size=101", null, dashboardToken).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-018: label 1-50 characters; unknown properties are rejected")
    void labelValidation() {
        for (Object label : List.of("", "   ", "x".repeat(51))) {
            assertThat(call(HttpMethod.POST, CREDENTIALS, Map.of("label", label), dashboardToken).getStatusCode())
                    .as("label '" + label + "'").isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(call(HttpMethod.POST, CREDENTIALS, Map.of("label", "ok", "status", "REVOKED"), dashboardToken)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.POST, CREDENTIALS, Map.of("label", "x".repeat(50)), dashboardToken).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM merchant_credentials", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("TST-029 FR-AUTH-023: create and revoke are audited with ids only (no secret, hash or label)")
    void audited() {
        Map<String, Object> credential = create("sensitive-label");
        call(HttpMethod.DELETE, CREDENTIALS + "/" + credential.get("id"), null, dashboardToken);

        for (String action : List.of("CREDENTIAL_CREATED", "CREDENTIAL_REVOKED")) {
            List<String> rows = jdbc.queryForList("SELECT payload::text FROM outbox_events WHERE routing_key = 'audit.recorded' "
                    + "AND payload->'data'->>'action' = ?", String.class, action);
            assertThat(rows).as(action).hasSize(1);
            assertThat(rows.get(0)).contains("\"actorUserId\": \"" + merchantUser.getId() + "\"", "\"actorRole\": \"MERCHANT\"",
                            "\"entityId\": \"" + credential.get("id") + "\"", "\"merchantId\": \"" + merchant.getId() + "\"")
                    .doesNotContain((String) credential.get("clientSecret"), "sensitive-label", "Hash");
        }
    }

    // ------------------------------------------------------------------ token endpoint rules

    @Test
    @DisplayName("TST-032 FR-AUTH-019: scope subset, invalid_scope, unsupported_grant_type, invalid_request, JSON body refused")
    void tokenRequestRules() throws Exception {
        Map<String, Object> c = create("one");
        String id = (String) c.get("clientId");
        String secret = (String) c.get("clientSecret");

        ResponseEntity<Map> subset = tokenRequest(form("client_credentials", id, secret, "orders:read"), null);
        assertThat(subset.getBody()).containsEntry("scope", "orders:read");
        assertThat(claims((String) subset.getBody().get("access_token"))).containsEntry("scope", "orders:read");

        ResponseEntity<Map> badScope = tokenRequest(form("client_credentials", id, secret, "orders:read admin"), null);
        assertThat(badScope.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badScope.getBody()).containsEntry("error", "invalid_scope");

        ResponseEntity<Map> grant = tokenRequest(form("password", id, secret, null), null);
        assertThat(grant.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(grant.getBody()).containsEntry("error", "unsupported_grant_type");

        ResponseEntity<Map> noGrant = tokenRequest(form(null, id, secret, null), null);
        assertThat(noGrant.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(noGrant.getBody()).containsEntry("error", "invalid_request");
        assertThat(tokenRequest(form("client_credentials", id, null, null), null).getBody())
                .containsEntry("error", "invalid_request");

        // unknown client: same answer as a wrong secret
        ResponseEntity<Map> unknown = tokenRequest(form("client_credentials", "sdc_doesnotexist", secret, null), null);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknown.getBody()).isEqualTo(tokenRequest(form("client_credentials", id, "sds_wrong", null), null).getBody());

        // JSON is not accepted: the endpoint is form-encoded
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> asJson = rest.exchange(TOKEN, HttpMethod.POST,
                new HttpEntity<>(Map.of("grant_type", "client_credentials", "client_id", id, "client_secret", secret), json),
                Map.class);
        // refused with a 4xx and no token (the exact status comes from auth-service's service-wide error handling)
        assertThat(asJson.getStatusCode().is4xxClientError()).isTrue();
        assertThat(String.valueOf(asJson.getBody())).doesNotContain("access_token");
    }

    @Test
    @DisplayName("TST-032 FR-AUTH-019: HTTP Basic client authentication is accepted; Basic plus body credentials is invalid_request")
    void basicAuthentication() {
        Map<String, Object> c = create("one");
        String id = (String) c.get("clientId");
        String secret = (String) c.get("clientSecret");
        String basic = "Basic " + Base64.getEncoder().encodeToString((id + ":" + secret).getBytes(StandardCharsets.UTF_8));

        ResponseEntity<Map> ok = tokenRequest(form("client_credentials", null, null, null), basic);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);

        String wrong = "Basic " + Base64.getEncoder().encodeToString((id + ":sds_wrong").getBytes(StandardCharsets.UTF_8));
        assertThat(tokenRequest(form("client_credentials", null, null, null), wrong).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(tokenRequest(form("client_credentials", id, secret, null), basic).getBody())
                .containsEntry("error", "invalid_request");
        assertThat(tokenRequest(form("client_credentials", null, null, null), "Basic !!!notbase64").getBody())
                .containsEntry("error", "invalid_request");
    }

    @Test
    @DisplayName("TST-032 BR-12 BR-23: pending, rejected and suspended merchants and deactivated users get invalid_client")
    void merchantStateBlocksTokens() {
        Map<String, Object> c = create("one");
        String id = (String) c.get("clientId");
        String secret = (String) c.get("clientSecret");
        assertThat(tokenRequest(form("client_credentials", id, secret, null), null).getStatusCode()).isEqualTo(HttpStatus.OK);

        for (MerchantStatus status : List.of(MerchantStatus.PENDING, MerchantStatus.REJECTED, MerchantStatus.SUSPENDED)) {
            merchant.setStatus(status);
            merchant = merchantRepository.save(merchant);
            ResponseEntity<Map> response = tokenRequest(form("client_credentials", id, secret, null), null);
            assertThat(response.getStatusCode()).as(status.name()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).as(status.name()).containsEntry("error", "invalid_client");
        }

        merchant.setStatus(MerchantStatus.APPROVED);
        merchant = merchantRepository.save(merchant);
        assertThat(tokenRequest(form("client_credentials", id, secret, null), null).getStatusCode()).isEqualTo(HttpStatus.OK);

        merchantUser.setStatus(UserStatus.DEACTIVATED);
        merchantUser = userRepository.save(merchantUser);
        assertThat(tokenRequest(form("client_credentials", id, secret, null), null).getBody())
                .containsEntry("error", "invalid_client");
    }

    // ------------------------------------------------------------------ TST-032 / TST-033 authorisation

    @Test
    @DisplayName("TST-032 TST-033: a typ=client token cannot call the credentials, /me or admin endpoints")
    void clientTokenCannotCallOtherEndpoints() {
        Map<String, Object> c = create("one");
        ResponseEntity<Map> token = tokenRequest(
                form("client_credentials", (String) c.get("clientId"), (String) c.get("clientSecret"), null), null);
        String clientToken = (String) token.getBody().get("access_token");

        // The shared token-type rule answers 403 ACCESS_DENIED once auth-service uses common-lib's security chain;
        // until then auth-service's own filter answers 401. Either way the endpoint must stay closed.
        for (String path : List.of(CREDENTIALS, "/api/v1/me", "/api/v1/admin/merchants")) {
            HttpStatus status = HttpStatus.valueOf(call(HttpMethod.GET, path, null, clientToken).getStatusCode().value());
            assertThat(status).as(path).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        }
        assertThat(HttpStatus.valueOf(call(HttpMethod.POST, CREDENTIALS, Map.of("label", "x"), clientToken)
                .getStatusCode().value())).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM merchant_credentials", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("TST-033 FR-AUTH-018: no token gives 401, CUSTOMER and ADMIN tokens give 403 on the credentials endpoints")
    void authorisation() {
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST)) {
            assertThat(call(method, CREDENTIALS, method == HttpMethod.POST ? Map.of("label", "x") : null, null)
                    .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(call(HttpMethod.DELETE, CREDENTIALS + "/" + UUID.randomUUID(), null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        User customer = createUser("customer@example.com", UserRole.CUSTOMER);
        User admin = createUser("admin@swiftdrop.lk", UserRole.ADMIN);
        for (User other : List.of(customer, admin)) {
            String token = jwtService.issueAccessToken(other.getId(), other.getRole().name(), false, null);
            assertThat(call(HttpMethod.GET, CREDENTIALS, null, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(call(HttpMethod.POST, CREDENTIALS, Map.of("label", "x"), token).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(call(HttpMethod.DELETE, CREDENTIALS + "/" + UUID.randomUUID(), null, token).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM merchant_credentials", Integer.class)).isZero();
    }

    @Test
    @DisplayName("TST-033 FR-AUTH-019: the token endpoint is public (no bearer token needed)")
    void tokenEndpointIsPublic() {
        ResponseEntity<Map> response = tokenRequest(form("client_credentials", "sdc_x", "sds_y", null), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "invalid_client"); // from the endpoint, not from security
    }

    // ------------------------------------------------------------------ helpers

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

    private Merchant createMerchant(User user, MerchantStatus status) {
        Merchant m = new Merchant(user.getId(), "Lanka Crafts " + user.getEmail(), status);
        m.setAddressLine1("12 Galle Road");
        m.setCity("Colombo");
        return merchantRepository.save(m);
    }

    private Map<String, Object> create(String label) {
        ResponseEntity<Map> response = call(HttpMethod.POST, CREDENTIALS, Map.of("label", label), dashboardToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        return body;
    }

    private static MultiValueMap<String, String> form(String grant, String id, String secret, String scope) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (grant != null) form.add("grant_type", grant);
        if (id != null) form.add("client_id", id);
        if (secret != null) form.add("client_secret", secret);
        if (scope != null) form.add("scope", scope);
        return form;
    }

    private ResponseEntity<Map> tokenRequest(MultiValueMap<String, String> form, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (authorization != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        return rest.exchange(TOKEN, HttpMethod.POST, new HttpEntity<>(form, headers), Map.class);
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
    private Map<String, Object> claims(String jwt) throws Exception {
        byte[] payload = Base64.getUrlDecoder().decode(jwt.split("\\.")[1]);
        return objectMapper.readValue(payload, Map.class);
    }
}

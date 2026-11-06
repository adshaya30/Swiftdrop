package com.swiftdrop.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftdrop.common.enums.Role;
import com.swiftdrop.common.http.ApiHeaders;
import com.swiftdrop.common.testapp.TestApp;
import com.swiftdrop.common.testsupport.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TestApp.class, properties = "INTERNAL_API_KEY=" + TestApp.INTERNAL_KEY)
@AutoConfigureMockMvc
@Tag("TST-013")
class SecurityIntegrationTest {

    private static final TestTokens T = TestApp.TOKENS;

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("TST-013 BR-25: public path needs no token, secured path answers 401 UNAUTHENTICATED")
    void publicAndUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/public/ping")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/secured"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("TST-013 valid token: CurrentUser is read from the claims")
    void validToken() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/secured").header(HttpHeaders.AUTHORIZATION, T.customer().userId(id).bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(id.toString()))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.typ").value("user"));
    }

    @Test
    @DisplayName("TST-013 expired token gives 401 TOKEN_EXPIRED")
    void expired() throws Exception {
        mvc.perform(get("/api/v1/secured").header(HttpHeaders.AUTHORIZATION, T.customer().expired().bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("TST-013 tampered, wrong audience, wrong issuer and foreign key tokens give 401 UNAUTHENTICATED")
    void invalidTokens() throws Exception {
        String good = T.customer().build();
        String tampered = good.substring(0, good.length() - 6) + "AAAAAA";
        String[] bad = {
                "Bearer " + tampered,
                T.customer().audience("other").bearer(),
                T.customer().issuer("evil").bearer(),
                T.customer().signedBy(TestApp.OTHER_TOKENS).bearer(),
                "Bearer not-a-jwt"};
        for (String header : bad) {
            mvc.perform(get("/api/v1/secured").header(HttpHeaders.AUTHORIZATION, header))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
    }

    @Test
    @DisplayName("TST-013 BR-25: wrong role gets 403 ACCESS_DENIED, admin passes")
    void roles() throws Exception {
        mvc.perform(get("/api/v1/admin-only").header(HttpHeaders.AUTHORIZATION, T.customer().bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/admin-only").header(HttpHeaders.AUTHORIZATION, T.admin().bearer()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TST-013 TST-032 scopes become SCOPE_ authorities")
    void scopes() throws Exception {
        UUID mid = UUID.randomUUID();
        mvc.perform(get("/api/v1/merchant/orders").header(HttpHeaders.AUTHORIZATION, T.client(mid, "orders:read").bearer()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/scoped").header(HttpHeaders.AUTHORIZATION, T.merchant(mid).scopes("orders:read").bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/scoped").header(HttpHeaders.AUTHORIZATION, T.merchant(mid).scopes("orders:write").bearer()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TST-032 token-type rule: typ=client only on /merchant/orders, 403 ACCESS_DENIED elsewhere")
    void clientTokenRule() throws Exception {
        String client = T.client(UUID.randomUUID(), "orders:read", "orders:write").bearer();
        mvc.perform(get("/api/v1/merchant/orders").header(HttpHeaders.AUTHORIZATION, client)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/merchant/orders/123").header(HttpHeaders.AUTHORIZATION, client)).andExpect(status().isOk());
        for (String path : new String[] {"/api/v1/merchant/credentials", "/api/v1/secured", "/api/v1/admin-only",
                "/api/v1/merchant/orders-evil"}) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, client))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        // the same merchant with a normal login token may use the dashboard endpoints
        mvc.perform(get("/api/v1/merchant/credentials")
                        .header(HttpHeaders.AUTHORIZATION, T.merchant(UUID.randomUUID()).bearer()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TST-013 pwc=true: 403 PASSWORD_CHANGE_REQUIRED except on the whitelist")
    void passwordChange() throws Exception {
        String pwc = T.customer().passwordChangeRequired(true).bearer();
        mvc.perform(get("/api/v1/secured").header(HttpHeaders.AUTHORIZATION, pwc))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(post("/api/v1/auth/change-password").header(HttpHeaders.AUTHORIZATION, pwc))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TST-013 /internal needs X-Internal-Key: missing or wrong 401, right 200, JWT is not used")
    void internalKey() throws Exception {
        mvc.perform(get("/internal/v1/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/internal/v1/ping").header(ApiHeaders.INTERNAL_KEY, "wrong-key"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/ping").header(HttpHeaders.AUTHORIZATION, T.admin().bearer()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/ping").header(ApiHeaders.INTERNAL_KEY, TestApp.INTERNAL_KEY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TST-013 an internal key does not open /api endpoints")
    void internalKeyNotForApi() throws Exception {
        mvc.perform(get("/api/v1/secured").header(ApiHeaders.INTERNAL_KEY, TestApp.INTERNAL_KEY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TST-013 request id is created, honoured and returned; unsafe ids are replaced")
    void requestId() throws Exception {
        mvc.perform(get("/api/v1/request-id").header(HttpHeaders.AUTHORIZATION, T.customer().bearer())
                        .header(ApiHeaders.REQUEST_ID, "abc-123"))
                .andExpect(header().string(ApiHeaders.REQUEST_ID, "abc-123"))
                .andExpect(jsonPath("$.requestId").value("abc-123"));
        mvc.perform(get("/api/v1/request-id").header(HttpHeaders.AUTHORIZATION, T.customer().bearer())
                        .header(ApiHeaders.REQUEST_ID, "bad id\r\nX: y"))
                .andExpect(header().exists(ApiHeaders.REQUEST_ID));
        mvc.perform(get("/api/v1/public/ping")).andExpect(header().exists(ApiHeaders.REQUEST_ID));
    }

    @Test
    @DisplayName("TST-013 health is public")
    void health() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().is(org.hamcrest.Matchers.not(401)));
    }

    @Test
    @DisplayName("TST-013 OwnershipPolicy: admin always, customer by id, merchant by mid (BR-25)")
    void ownership() {
        UUID id = UUID.randomUUID();
        UUID mid = UUID.randomUUID();
        var customer = CurrentUsers.from(T.decode(T.customer().userId(id).build()));
        var merchant = CurrentUsers.from(T.decode(T.merchant(mid).build()));
        var admin = CurrentUsers.from(T.decode(T.admin().build()));
        var courier = CurrentUsers.from(T.decode(T.token().role(Role.COURIER).build()));
        org.assertj.core.api.Assertions.assertThat(OwnershipPolicy.isOwnerOrAdmin(admin, null, null)).isTrue();
        org.assertj.core.api.Assertions.assertThat(OwnershipPolicy.isOwnerOrAdmin(customer, id, null)).isTrue();
        org.assertj.core.api.Assertions.assertThat(OwnershipPolicy.isOwnerOrAdmin(customer, UUID.randomUUID(), mid)).isFalse();
        org.assertj.core.api.Assertions.assertThat(OwnershipPolicy.isOwnerOrAdmin(merchant, null, mid)).isTrue();
        org.assertj.core.api.Assertions.assertThat(OwnershipPolicy.isOwnerOrAdmin(merchant, id, UUID.randomUUID())).isFalse();
        org.assertj.core.api.Assertions.assertThat(OwnershipPolicy.isOwnerOrAdmin(courier, id, mid)).isFalse();
        org.assertj.core.api.Assertions.assertThat(OwnershipPolicy.isOwnerOrAdmin(null, id, mid)).isFalse();
    }
}

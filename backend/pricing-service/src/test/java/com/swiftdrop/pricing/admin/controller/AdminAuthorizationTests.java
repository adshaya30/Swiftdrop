package com.swiftdrop.pricing.admin.controller;

import static com.swiftdrop.pricing.support.PricingTestConfiguration.TOKENS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftdrop.pricing.support.AbstractPricingDatabaseTest;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** TST-033 BR-25 SRS 3.6: every pricing /admin/** endpoint is ADMIN only; client tokens are refused. */
@Tag("TST-033")
@Transactional
class AdminAuthorizationTests extends AbstractPricingDatabaseTest {
    private static final String ADMIN = "/api/v1/admin";
    private static final String ID = "f80dc738-67e9-4b29-9109-5eee95536727";

    @Autowired MockMvc mvc;

    /** Valid bodies, so that the role check, not validation, decides the outcome. */
    static Stream<Arguments> endpoints() {
        String surcharges = "[{\"size\":\"SMALL\",\"surcharge\":1},{\"size\":\"MEDIUM\",\"surcharge\":2},"
                + "{\"size\":\"LARGE\",\"surcharge\":3}]";
        return Stream.of(
                Arguments.of(HttpMethod.GET, ADMIN + "/zones", null),
                Arguments.of(HttpMethod.POST, ADMIN + "/zones", "{\"code\":\"NEW_ZONE\",\"name\":\"New Zone\"}"),
                Arguments.of(HttpMethod.PATCH, ADMIN + "/zones/" + ID, "{\"active\":true}"),
                Arguments.of(HttpMethod.PUT, ADMIN + "/districts/KANDY/zone", "{\"zoneId\":\"" + ID + "\"}"),
                Arguments.of(HttpMethod.GET, ADMIN + "/pricing", null),
                Arguments.of(HttpMethod.PUT, ADMIN + "/pricing/" + ID + "/" + ID, "{\"baseFee\":1,\"perKgFee\":1}"),
                Arguments.of(HttpMethod.GET, ADMIN + "/size-surcharges", null),
                Arguments.of(HttpMethod.PUT, ADMIN + "/size-surcharges", surcharges));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("endpoints")
    @DisplayName("TST-033 BR-25: unauthenticated callers get 401")
    void unauthenticated(HttpMethod method, String path, String body) throws Exception {
        var builder = request(method, path).contentType("application/json");
        if (body != null) {
            builder.content(body);
        }
        mvc.perform(builder).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("endpoints")
    @DisplayName("TST-033 BR-25: CUSTOMER, MERCHANT, COURIER and client tokens get 403 ACCESS_DENIED")
    void otherRolesAndClientTokens(HttpMethod method, String path, String body) throws Exception {
        String[] tokens = {TOKENS.customer().bearer(), TOKENS.merchant(UUID.randomUUID()).bearer(),
                TOKENS.courier().bearer(), TOKENS.client(UUID.randomUUID(), "orders:write").bearer()};
        for (String token : tokens) {
            var builder = request(method, path).contentType("application/json").header("Authorization", token);
            if (body != null) {
                builder.content(body);
            }
            mvc.perform(builder).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }
}

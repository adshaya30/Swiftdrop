package com.swiftdrop.parcel.order.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.swiftdrop.common.client.InternalRestClientFactory;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.security.InternalApiKey;
import com.swiftdrop.parcel.order.client.AuthClient;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** BR-23 re-check against a WireMock stub of the SRS 7.4 auth-service contract. */
@Tag("TST-032")
class MerchantEligibilityTests {

    private static final WireMockServer AUTH = new WireMockServer(options().dynamicPort());
    private static final String KEY = "eligibility-test-key-0123456789-abcdefghij";

    private final UUID mid = UUID.randomUUID();
    private MerchantEligibility eligibility;

    @BeforeAll
    static void start() {
        AUTH.start();
    }

    @AfterAll
    static void stop() {
        AUTH.stop();
    }

    @BeforeEach
    void setUp() {
        AUTH.resetAll();
        var factory = new InternalRestClientFactory(new InternalApiKey(KEY), new ObjectMapper());
        eligibility = new MerchantEligibility(new AuthClient(factory, AUTH.baseUrl()));
    }

    @Test
    @DisplayName("BR-23 FR-PAR-007: an APPROVED merchant with an ACTIVE user gets its discount for pricing")
    void approvedActive() {
        stub("APPROVED", "ACTIVE", "10.00");

        assertThat(eligibility.requireActive(mid)).isEqualByComparingTo("10.00");
        AUTH.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                        com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/internal/v1/merchants/" + mid))
                .withHeader("X-Internal-Key", com.github.tomakehurst.wiremock.client.WireMock.equalTo(KEY)));
    }

    @Test
    @DisplayName("BR-13: a missing discount counts as 0")
    void missingDiscount() {
        stub("APPROVED", "ACTIVE", "null");

        assertThat(eligibility.requireActive(mid)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @ParameterizedTest
    @CsvSource({"PENDING,ACTIVE", "REJECTED,ACTIVE", "SUSPENDED,ACTIVE", "APPROVED,DEACTIVATED"})
    @DisplayName("BR-12 BR-23: a merchant not APPROVED or a user not ACTIVE gets 403 MERCHANT_NOT_ACTIVE")
    void notActive(String status, String userStatus) {
        stub(status, userStatus, "0.00");

        assertThatThrownBy(() -> eligibility.requireActive(mid))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MERCHANT_NOT_ACTIVE));
    }

    @Test
    @DisplayName("BR-23: a merchant unknown to auth-service is not active")
    void unknownMerchant() {
        AUTH.stubFor(get("/internal/v1/merchants/" + mid).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"status\":404,\"code\":\"NOT_FOUND\",\"detail\":\"Merchant not found\"}")));

        assertThatThrownBy(() -> eligibility.requireActive(mid))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MERCHANT_NOT_ACTIVE));
    }

    @Test
    @DisplayName("SRS 3.3: auth-service failing gives 503 SERVICE_UNAVAILABLE")
    void authDown() {
        AUTH.stubFor(get("/internal/v1/merchants/" + mid).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> eligibility.requireActive(mid))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
    }

    private void stub(String status, String userStatus, String discount) {
        AUTH.stubFor(get("/internal/v1/merchants/" + mid).willReturn(okJson("""
                {"id":"%s","userId":"%s","businessName":"Test Store","status":"%s","userStatus":"%s",
                 "discountPercent":%s,"contactEmail":"store@example.invalid"}
                """.formatted(mid, UUID.randomUUID(), status, userStatus, discount))));
    }
}

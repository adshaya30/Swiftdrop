package com.swiftdrop.parcel.create.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.swiftdrop.common.client.InternalRestClientFactory;
import com.swiftdrop.common.enums.ParcelSize;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.error.FieldError;
import com.swiftdrop.common.security.InternalApiKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.create.dto.ParcelInput;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.*;

@Tag("TST-021")
class PricingClientTests {
    private WireMockServer server;
    private PricingClient client;
    private String baseUrl;
    private final String key = "pricing-contract-" + java.util.UUID.randomUUID();
    private static final String PATH = "/internal/v1/pricing/calculate";

    @BeforeEach
    void start() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        baseUrl = server.baseUrl();
        client = new PricingClient(new InternalRestClientFactory(new InternalApiKey(key),
                new ObjectMapper()), baseUrl);
    }

    @AfterEach
    void stop() {
        MDC.clear();
        server.stop();
    }

    private ParcelInput input() {
        return new ParcelInput(
                new ParcelInput.Pickup("Sender", "0771234567", "Test", null, "Colombo", "COLOMBO", null),
                new ParcelInput.Dropoff("Receiver", "0712345678", "Test", null, "Kandy", "KANDY", null, null),
                new ParcelInput.ParcelData(new BigDecimal("2.00"), ParcelSize.MEDIUM, null), null);
    }

    @Test
    void preservesExactInternalContractHeadersAndAuthoritativeDecimals() {
        MDC.put("requestId", "pricing-contract-trace");
        server.stubFor(post(PATH).willReturn(okJson("""
                {"currency":"LKR","originZone":{"code":"WESTERN","name":"Western"},
                "destinationZone":{"code":"CENTRAL","name":"Central"},
                "baseFee":500.00,"weightFee":150.00,"sizeSurcharge":150.00,
                "discountAmount":80.00,"total":720.00,
                "originDistrict":{"code":"COLOMBO","name":"Colombo"},
                "destinationDistrict":{"code":"KANDY","name":"Kandy"},
                "subtotal":800.00,"discountPercent":10.00}
                """)));
        var result = client.calculate(input(), new BigDecimal("10"));
        assertThat(result.total()).isEqualByComparingTo("720.00");
        assertThat(result.subtotal()).isEqualByComparingTo("800.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("80.00");
        assertThat(result.originDistrict().code()).isEqualTo("COLOMBO");
        assertThat(result.destinationZone().name()).isEqualTo("Central");
        server.verify(postRequestedFor(urlEqualTo(PATH))
                .withHeader("X-Internal-Key", equalTo(key))
                .withHeader("X-Request-Id", equalTo("pricing-contract-trace"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(equalToJson("""
                        {"originDistrictCode":"COLOMBO","destinationDistrictCode":"KANDY",
                        "weightKg":2.00,"size":"MEDIUM","discountPercent":10}
                        """)));
    }

    @ParameterizedTest
    @CsvSource({"400,VALIDATION_ERROR", "422,ZONE_NOT_SERVICEABLE", "422,PRICING_NOT_CONFIGURED"})
    void preservesPricingBusinessErrors(int status, ErrorCode code) {
        server.stubFor(post(PATH).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/problem+json").withBody("""
                        {"code":"%s","detail":"Pricing request rejected",
                        "errors":[{"field":"weightKg","message":"Outside supported range"}]}
                        """.formatted(code))));
        assertThatThrownBy(() -> client.calculate(input(), BigDecimal.ZERO))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(code);
                    assertThat(e.code().status()).isEqualTo(status);
                    assertThat(e.detail()).isEqualTo("Pricing request rejected");
                    assertThat(e.errors()).containsExactly(new FieldError("weightKg", "Outside supported range"));
                });
    }

    @ParameterizedTest
    @CsvSource({"500", "502", "503"})
    void mapsServerErrorsToSafe503(int status) {
        server.stubFor(post(PATH).willReturn(aResponse().withStatus(status).withBody("private server error")));
        assertUnavailable();
    }

    @Test
    void mapsConnectionFailureTo503() {
        server.stop();
        assertUnavailable();
    }

    @Test
    void sharedTenSecondReadTimeoutMapsTo503() {
        server.stubFor(post(PATH).willReturn(okJson("{}").withFixedDelay(11000)));
        assertUnavailable();
    }

    @ParameterizedTest
    @CsvSource({"''", "'invalid-json'"})
    void mapsEmptyOrUnreadableSuccessTo503(String body) {
        server.stubFor(post(PATH).willReturn(okJson(body)));
        assertUnavailable();
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> client.calculate(input(), BigDecimal.ZERO))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
                    assertThat(e.code().status()).isEqualTo(503);
                    assertThat(e.detail()).doesNotContain("private", baseUrl);
                });
    }
}


package com.swiftdrop.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "PUBLIC_BASE_URL=http://localhost:8080",
        "RATE_LIMIT_ENABLED=false",
        "HSTS_ENABLED=false",
        "management.server.port=0"
})
class GatewayApplicationTests {

    @Autowired
    private WebTestClient webTestClient;

    @LocalManagementPort
    private int managementPort;

    @LocalServerPort
    private int gatewayPort;

    @Test
    @DisplayName("TST-GW-001 gateway blocks actuator and internal routes")
    void actuatorAndInternalPathsAreNotProxied() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.get()
                .uri("/internal/v1/test")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("TST-GW-002 unknown API paths return problem JSON and request IDs")
    void unknownApiPathsReturnProblemJson() {
        webTestClient.get()
                .uri("/api/does-not-exist")
                .header("X-Request-Id", "gateway-test-123")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader()
                .valueEquals("X-Request-Id", "gateway-test-123")
                .expectHeader()
                .valueMatches("X-Content-Type-Options", "nosniff")
                .expectHeader()
                .valueMatches(
                        "Permissions-Policy",
                        "camera=\\(self\\), geolocation=\\(\\)"
                )
                .expectHeader()
                .valueMatches(
                        "Content-Security-Policy",
                        "default-src 'self'.*"
                )
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("NOT_FOUND")
                .jsonPath("$.title")
                .isEqualTo("Resource not found");
    }

    @Test
    @DisplayName("TST-GW-003 request body over the limit is rejected with 413")
    void oversizedBodiesAreRejected() {
        String largeBody = "x".repeat(11 * 1024 * 1024 + 1);

        webTestClient.post()
                .uri("/api/v1/courier/deliveries/123/status")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(largeBody)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
                .expectHeader()
                .exists("X-Request-Id")
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    @DisplayName("TST-GW-003a ordinary requests over 1 MiB are rejected")
    void oversizedOrdinaryRequestIsRejected() {
        webTestClient.post()
                .uri("/api/v1/customer/parcels")
                .header(
                        HttpHeaders.CONTENT_LENGTH,
                        String.valueOf(1024L * 1024L + 1L)
                )
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    @DisplayName("TST-GW-004 proxied routes surface upstream failures as gateway errors")
    void upstreamFailuresBecomeGatewayErrors() {
        webTestClient.get()
                .uri("/api/v1/auth/login")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectHeader()
                .exists("X-Request-Id")
                .expectHeader()
                .valueMatches("X-Content-Type-Options", "nosniff")
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("TST-GW-005 CORS is restricted to the configured public origin")
    void corsIsRestrictedToPublicOrigin() {
        WebTestClient corsClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .build();

        corsClient.options()
                .uri("/api/v1/public/quotes")
                .header(HttpHeaders.ORIGIN, "http://localhost:8080")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader()
                .valueEquals(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:8080"
                );

        corsClient.options()
                .uri("/api/v1/public/quotes")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader()
                .valueEquals(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"
                );

        corsClient.options()
                .uri("/api/v1/public/quotes")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("TST-GW-006 management health remains available on the management port")
    void managementHealthIsAvailable() {

        org.assertj.core.api.Assertions
                .assertThat(managementPort)
                .isNotEqualTo(gatewayPort);

        WebTestClient managementClient = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + managementPort)
                .build();

        managementClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();

        managementClient.get()
                .uri("/actuator/health/liveness")
                .exchange()
                .expectStatus().isOk();

        managementClient.get()
                .uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk();
    }
}

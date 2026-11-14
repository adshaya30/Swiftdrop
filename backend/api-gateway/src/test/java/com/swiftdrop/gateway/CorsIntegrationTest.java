package com.swiftdrop.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class CorsIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("AUTH_SERVICE_HOST", () -> "localhost");
        registry.add("AUTH_SERVICE_PORT", () -> wireMockServer.port());
        registry.add("AUTH_SERVICE_URL", () -> "http://localhost:" + wireMockServer.port());
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Preflight (OPTIONS) Tests
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void testPreflightConfig() {
        EntityExchangeResult<Void> result = webTestClient.options()
                .uri("http://localhost:8080/api/v1/auth/config")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,Authorization,Accept,Origin")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectBody().isEmpty();

        List<String> allowMethods = result.getResponseHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);
        assertThat(allowMethods).isNotEmpty();
        String methodsString = String.join(",", allowMethods);
        assertThat(methodsString).contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

        List<String> allowHeaders = result.getResponseHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
        assertThat(allowHeaders).isNotEmpty();
        String headersString = String.join(",", allowHeaders);
        assertThat(headersString).contains("Content-Type", "Authorization", "Accept", "Origin");
    }

    @Test
    void testPreflightRegister() {
        EntityExchangeResult<Void> result = webTestClient.options()
                .uri("http://localhost:8080/api/v1/auth/register")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,Authorization,Accept,Origin")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectBody().isEmpty();

        List<String> allowMethods = result.getResponseHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);
        assertThat(allowMethods).isNotEmpty();
        assertThat(String.join(",", allowMethods)).contains("POST");
    }

    @Test
    void testPreflightRefresh() {
        EntityExchangeResult<Void> result = webTestClient.options()
                .uri("http://localhost:8080/api/v1/auth/refresh")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,Authorization,Accept,Origin,X-Requested-With")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectBody().isEmpty();

        List<String> allowHeaders = result.getResponseHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
        assertThat(allowHeaders).isNotEmpty();
        assertThat(String.join(",", allowHeaders)).contains("X-Requested-With");
    }

    @Test
    void testPreflightUnauthorizedOriginRejected() {
        webTestClient.options()
                .uri("http://localhost:8080/api/v1/auth/config")
                .header(HttpHeaders.ORIGIN, "http://malicious-site.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Actual HTTP Requests & Header Deduplication Tests
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    void testActualGetConfigWithNoDuplicateCorsHeaders() {
        // Downstream auth-service also sets CORS headers
        wireMockServer.stubFor(get(urlEqualTo("/api/v1/auth/config"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                        .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                        .withBody("{\"googleClientId\":\"test-client-id\"}")));

        EntityExchangeResult<byte[]> result = webTestClient.get()
                .uri("http://localhost:8080/api/v1/auth/config")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectBody().json("{\"googleClientId\":\"test-client-id\"}")
                .returnResult();

        // Verify NO duplicate Access-Control-Allow-Origin or Access-Control-Allow-Credentials headers
        List<String> allowOrigins = result.getResponseHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat(allowOrigins).containsExactly("http://localhost:5173");

        List<String> allowCreds = result.getResponseHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
        assertThat(allowCreds).containsExactly("true");
    }

    @Test
    void testActualPostRegisterWithNoDuplicateCorsHeaders() {
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/auth/register"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                        .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                        .withBody("{\"id\":\"user-uuid\",\"email\":\"test@example.com\"}")));

        EntityExchangeResult<byte[]> result = webTestClient.post()
                .uri("http://localhost:8080/api/v1/auth/register")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue("{\"email\":\"test@example.com\",\"password\":\"SecurePass123!\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectBody().json("{\"id\":\"user-uuid\",\"email\":\"test@example.com\"}")
                .returnResult();

        List<String> allowOrigins = result.getResponseHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat(allowOrigins).containsExactly("http://localhost:5173");

        List<String> allowCreds = result.getResponseHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
        assertThat(allowCreds).containsExactly("true");
    }

    @Test
    void testActualPostRefreshWithCookieAndNoDuplicateCorsHeaders() {
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/auth/refresh"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Set-Cookie", "sd_refresh=valid-token; Path=/api/v1/auth; HttpOnly; SameSite=Lax")
                        .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                        .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                        .withBody("{\"accessToken\":\"new-jwt-token\"}")));

        EntityExchangeResult<byte[]> result = webTestClient.post()
                .uri("http://localhost:8080/api/v1/auth/refresh")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header("X-Requested-With", "SwiftDrop")
                .cookie("sd_refresh", "old-token")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectHeader().exists("Set-Cookie")
                .expectBody().json("{\"accessToken\":\"new-jwt-token\"}")
                .returnResult();

        List<String> allowOrigins = result.getResponseHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        assertThat(allowOrigins).containsExactly("http://localhost:5173");

        List<String> allowCreds = result.getResponseHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
        assertThat(allowCreds).containsExactly("true");
    }
}

package com.swiftdrop.common.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.security.InternalApiKey;
import com.swiftdrop.common.testapp.TestApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

@Tag("TST-013")
class InternalRestClientFactoryTest {

    static WireMockServer server;
    RestClient client;

    @BeforeAll
    static void start() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterAll
    static void stop() {
        server.stop();
    }

    @BeforeEach
    void setUp() {
        server.resetAll();
        client = new InternalRestClientFactory(new InternalApiKey(TestApp.INTERNAL_KEY), new ObjectMapper())
                .create("http://localhost:" + server.port());
    }

    @Test
    @DisplayName("TST-013 success: sends X-Internal-Key and propagates X-Request-Id")
    void sendsHeaders() {
        server.stubFor(get(urlEqualTo("/internal/v1/ping")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"ok\":true}")));
        MDC.put("requestId", "req-42");
        try {
            String body = client.get().uri("/internal/v1/ping").retrieve().body(String.class);
            assertThat(body).contains("ok");
        } finally {
            MDC.remove("requestId");
        }
        server.verify(getRequestedFor(urlEqualTo("/internal/v1/ping"))
                .withHeader("X-Internal-Key", equalTo(TestApp.INTERNAL_KEY))
                .withHeader("X-Request-Id", equalTo("req-42")));
    }

    @Test
    @DisplayName("TST-013 callee business error is rethrown with the same code, status, detail and extensions")
    void businessErrorUnchanged() {
        server.stubFor(get(urlEqualTo("/internal/v1/x")).willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"type\":\"t\",\"title\":\"Fee changed\",\"status\":409,\"detail\":\"Fee moved\","
                        + "\"code\":\"FEE_CHANGED\",\"newFee\":\"700.00\"}")));
        assertThatThrownBy(() -> client.get().uri("/internal/v1/x").retrieve().body(String.class))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.FEE_CHANGED);
                    assertThat(e.detail()).isEqualTo("Fee moved");
                    assertThat(e.extensions()).containsEntry("newFee", "700.00");
                });
    }

    @Test
    @DisplayName("TST-013 5xx and unparsable errors become 503 SERVICE_UNAVAILABLE")
    void serverErrorBecomes503() {
        server.stubFor(get(urlEqualTo("/internal/v1/boom")).willReturn(aResponse().withStatus(500)
                .withBody("{\"code\":\"INTERNAL_ERROR\"}")));
        server.stubFor(get(urlEqualTo("/internal/v1/junk")).willReturn(aResponse().withStatus(404).withBody("<html>")));
        for (String path : new String[] {"/internal/v1/boom", "/internal/v1/junk"}) {
            assertThatThrownBy(() -> client.get().uri(path).retrieve().body(String.class))
                    .isInstanceOfSatisfying(DomainException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
        }
    }

    @Test
    @DisplayName("TST-013 connection failure becomes 503 SERVICE_UNAVAILABLE")
    void connectionFailure() {
        RestClient dead = new InternalRestClientFactory(new InternalApiKey(TestApp.INTERNAL_KEY), new ObjectMapper())
                .create("http://localhost:1");
        assertThatThrownBy(() -> dead.get().uri("/internal/v1/ping").retrieve().body(String.class))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
    }
}

package com.swiftdrop.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class GatewayConfigurationTests {

    @Test
    @DisplayName("TST-GW-035 chunked bodies are counted while they stream")
    void chunkedBodiesCannotBypassDefaultLimit() {
        Flux<org.springframework.core.io.buffer.DataBuffer> chunks = Flux.range(0, 17)
                .map(ignored -> DefaultDataBufferFactory.sharedInstance.wrap(new byte[64 * 1024]));
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/customer/parcels").body(chunks);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilter filter = new GatewayConfiguration().requestBodyLimitFilter();
        WebFilterChain chain = filteredExchange -> filteredExchange.getRequest().getBody().then();

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                })
                .verify();
    }

    @Test
    @DisplayName("TST-GW-035 courier status route gets the 11 MiB limit")
    void courierStatusRouteUsesExtendedContentLengthLimit() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/courier/deliveries/123/status")
                .header("Content-Length", String.valueOf(11L * 1024L * 1024L + 1L))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilter filter = new GatewayConfiguration().requestBodyLimitFilter();

        StepVerifier.create(filter.filter(exchange, filteredExchange -> reactor.core.publisher.Mono.empty()))
                .expectComplete()
                .verify();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }
}

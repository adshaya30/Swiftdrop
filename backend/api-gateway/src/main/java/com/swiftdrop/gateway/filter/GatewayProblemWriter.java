package com.swiftdrop.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.error.ProblemBody;
import com.swiftdrop.common.http.ApiHeaders;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public final class GatewayProblemWriter {

    private static final Logger log = LoggerFactory.getLogger(GatewayProblemWriter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private GatewayProblemWriter() {
    }

    public static Mono<Void> write(ServerWebExchange exchange, ErrorCode code, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        String requestId = exchange.getRequest().getHeaders().getFirst(ApiHeaders.REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = String.valueOf(exchange.getAttributes().getOrDefault(ApiHeaders.REQUEST_ID, "unknown"));
        }
        response.setStatusCode(HttpStatus.valueOf(code.status()));
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().set(ApiHeaders.REQUEST_ID, requestId);
        response.getHeaders().set("X-Content-Type-Options", "nosniff");
        response.getHeaders().set("X-Frame-Options", "DENY");
        response.getHeaders().set("Referrer-Policy", "strict-origin-when-cross-origin");
        try {
            String body = OBJECT_MAPPER.writeValueAsString(ProblemBody.of(code, detail, requestId));
            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException ex) {
            log.atError()
                    .addKeyValue("requestId", requestId)
                    .setCause(ex)
                    .log("Failed to serialize gateway problem response");
            String body = "{\"type\":\"https://swiftdrop.example/errors/internal-error\","
                    + "\"title\":\"Internal error\",\"status\":500,\"detail\":\"Internal server error\","
                    + "\"code\":\"INTERNAL_ERROR\",\"traceId\":\"" + escapeJson(requestId) + "\"}";
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}

package com.swiftdrop.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.error.ProblemBody;
import com.swiftdrop.common.http.ApiHeaders;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

@Component
@Order(-1)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable throwable) {
        ServerHttpResponse response = exchange.getResponse();
        String requestId = exchange.getRequest().getHeaders().getFirst(ApiHeaders.REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = String.valueOf(exchange.getAttributes().getOrDefault(ApiHeaders.REQUEST_ID, "unknown"));
        }
        if (response.isCommitted()) {
            log.atError()
                    .addKeyValue("requestId", requestId)
                    .setCause(throwable)
                    .log("Gateway failure after response was committed");
            return Mono.error(throwable);
        }

        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        String detail = "Internal server error";
        ResponseStatusException statusException = findCause(throwable, ResponseStatusException.class);
        if (statusException != null) {
            HttpStatus status = HttpStatus.valueOf(statusException.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) {
                code = ErrorCode.NOT_FOUND;
                detail = "Resource not found";
            } else if (status == HttpStatus.PAYLOAD_TOO_LARGE) {
                code = ErrorCode.PAYLOAD_TOO_LARGE;
                detail = "Payload too large";
            } else if (status == HttpStatus.TOO_MANY_REQUESTS) {
                code = ErrorCode.RATE_LIMITED;
                detail = "Too many requests";
            } else if (status == HttpStatus.SERVICE_UNAVAILABLE) {
                code = unavailableCode(exchange);
                detail = code == ErrorCode.ASSISTANT_UNAVAILABLE ? "Assistant unavailable" : "Service unavailable";
            }
        } else if (isUpstreamUnavailable(throwable)) {
            code = unavailableCode(exchange);
            detail = code == ErrorCode.ASSISTANT_UNAVAILABLE ? "Assistant unavailable" : "Service unavailable";
        }

        response.setStatusCode(HttpStatus.valueOf(code.status()));
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().set(ApiHeaders.REQUEST_ID, requestId);
        if (code == ErrorCode.RATE_LIMITED) {
            response.getHeaders().set(ApiHeaders.RETRY_AFTER, "1");
        }
        response.getHeaders().set("X-Content-Type-Options", "nosniff");
        response.getHeaders().set("X-Frame-Options", "DENY");
        response.getHeaders().set("Referrer-Policy", "strict-origin-when-cross-origin");

        log.atError()
                .addKeyValue("requestId", requestId)
                .addKeyValue("errorCode", code.name())
                .setCause(throwable)
                .log("Gateway request failed");
        ProblemBody body = ProblemBody.of(code, detail, requestId);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception exception) {
            log.atWarn()
                    .addKeyValue("requestId", requestId)
                    .setCause(exception)
                    .log("Failed to serialize gateway problem response");
            bytes = ("{\"type\":\"https://swiftdrop.example/errors/internal-error\","
                    + "\"title\":\"Internal error\",\"status\":500,\"detail\":\"Internal server error\","
                    + "\"code\":\"INTERNAL_ERROR\",\"traceId\":\"" + requestId.replace("\"", "\\\"") + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private static ErrorCode unavailableCode(ServerWebExchange exchange) {
        Object route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return route instanceof org.springframework.cloud.gateway.route.Route gatewayRoute
                && "assistant".equals(gatewayRoute.getId())
                ? ErrorCode.ASSISTANT_UNAVAILABLE : ErrorCode.SERVICE_UNAVAILABLE;
    }

    private static boolean isUpstreamUnavailable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof WebClientRequestException
                    || cause instanceof ConnectException
                    || cause instanceof UnknownHostException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable cause = throwable;
        while (cause != null) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
            cause = cause.getCause();
        }
        return null;
    }
}

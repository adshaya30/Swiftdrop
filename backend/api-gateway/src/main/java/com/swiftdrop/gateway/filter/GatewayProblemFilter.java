package com.swiftdrop.gateway.filter;

import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.http.ApiHeaders;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public final class GatewayProblemFilter implements GatewayFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayProblemFilter.class);
    private final ErrorCode code;
    private final String detail;

    public GatewayProblemFilter(ErrorCode code, String detail) {
        this.code = code;
        this.detail = detail;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .onErrorResume(exception -> {
                    if (!isUpstreamUnavailable(exception)) {
                        return Mono.error(exception);
                    }
                    String requestId = exchange.getAttributeOrDefault(ApiHeaders.REQUEST_ID, "unknown");
                    if (exchange.getResponse().isCommitted()) {
                        log.atWarn()
                                .addKeyValue("requestId", requestId)
                                .addKeyValue("errorCode", code.name())
                                .setCause(exception)
                                .log("Gateway upstream failed after response commitment");
                        return Mono.error(exception);
                    }
                    log.atWarn()
                            .addKeyValue("requestId", requestId)
                            .addKeyValue("errorCode", code.name())
                            .setCause(exception)
                            .log("Gateway upstream is unavailable");
                    return GatewayProblemWriter.write(exchange, code, detail);
                });
    }

    private static boolean isUpstreamUnavailable(Throwable exception) {
        Throwable cause = exception;
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
}

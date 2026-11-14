package com.swiftdrop.gateway.config;

import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.http.ApiHeaders;
import com.swiftdrop.gateway.filter.GatewayProblemFilter;
import com.swiftdrop.gateway.filter.GatewayProblemWriter;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfiguration {

    private static final long DEFAULT_BODY_LIMIT = 1024L * 1024L;
    private static final long COURIER_STATUS_BODY_LIMIT = 11L * 1024L * 1024L;
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; img-src 'self' data: blob:; "
            + "style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self'; "
            + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    @Bean
    RouteLocator gatewayRoutes(RouteLocatorBuilder builder, Environment environment) {
        RouteLocatorBuilder.Builder routes = builder.routes();
        String auth = environment.getProperty("AUTH_SERVICE_URL", "http://auth-service:8081");
        String parcel = environment.getProperty("PARCEL_SERVICE_URL", "http://parcel-service:8082");
        String pricing = environment.getProperty("PRICING_SERVICE_URL", "http://pricing-service:8083");
        String delivery = environment.getProperty("DELIVERY_SERVICE_URL", "http://delivery-service:8084");
        String tracking = environment.getProperty("TRACKING_SERVICE_URL", "http://tracking-service:8085");
        String notification = environment.getProperty("NOTIFICATION_SERVICE_URL", "http://notification-service:8086");
        String assistant = environment.getProperty("AI_ASSISTANT_URL", "http://ai-assistant:8000");
        String frontend = environment.getProperty("FRONTEND_URL", "http://frontend:8080");

        routes.route("blocked-internal-paths", route -> route
                .path("/actuator", "/actuator/**", "/internal", "/internal/**")
                .filters(filters -> filters.filter(problemResponse(ErrorCode.NOT_FOUND, "Resource not found")))
                .uri("http://localhost"));
        addProxyRoute(routes, "assistant", assistant, ErrorCode.ASSISTANT_UNAVAILABLE,
                "/api/v1/assistant", "/api/v1/assistant/**", "/mcp");
        addProxyRoute(routes, "auth", auth, ErrorCode.SERVICE_UNAVAILABLE,
                "/api/v1/auth/**", "/api/v1/oauth/**", "/api/v1/me", "/api/v1/me/**",
                "/api/v1/merchant/credentials", "/api/v1/merchant/credentials/**",
                "/api/v1/admin/users", "/api/v1/admin/users/**",
                "/api/v1/admin/merchants", "/api/v1/admin/merchants/**",
                "/api/v1/admin/dashboard/merchants");
        addProxyRoute(routes, "pricing", pricing, ErrorCode.SERVICE_UNAVAILABLE,
                "/api/v1/public/districts", "/api/v1/public/quotes",
                "/api/v1/admin/zones", "/api/v1/admin/zones/**",
                "/api/v1/admin/districts", "/api/v1/admin/districts/**",
                "/api/v1/admin/pricing", "/api/v1/admin/pricing/**",
                "/api/v1/admin/size-surcharges");
        addProxyRoute(routes, "parcel", parcel, ErrorCode.SERVICE_UNAVAILABLE,
                "/api/v1/customer/**", "/api/v1/merchant/orders", "/api/v1/merchant/orders/**",
                "/api/v1/merchant/dashboard/**", "/api/v1/parcels/**",
                "/api/v1/admin/parcels", "/api/v1/admin/parcels/**",
                "/api/v1/admin/reports/**", "/api/v1/admin/dashboard/parcels");
        addProxyRoute(routes, "delivery", delivery, ErrorCode.SERVICE_UNAVAILABLE,
                "/api/v1/courier/**", "/api/v1/admin/deliveries", "/api/v1/admin/deliveries/**",
                "/api/v1/admin/couriers", "/api/v1/admin/couriers/**",
                "/api/v1/admin/dashboard/dispatch", "/api/v1/proofs/**");
        routes.route("tracking", route -> route
                .path("/api/v1/public/tracking/**", "/api/v1/tracking/**", "/api/v1/admin/audit-logs")
                .filters(filters -> filters
                        .removeRequestHeader(ApiHeaders.INTERNAL_KEY)
                        .filter(new GatewayProblemFilter(ErrorCode.SERVICE_UNAVAILABLE, "Service unavailable")))
                .uri(tracking));
        addProxyRoute(routes, "notification", notification, ErrorCode.SERVICE_UNAVAILABLE,
                "/api/v1/merchant/webhook", "/api/v1/merchant/webhook/**");
        routes.route("unknown-api", route -> route
                .path("/api", "/api/**")
                .filters(filters -> filters.filter(problemResponse(ErrorCode.NOT_FOUND, "Resource not found")))
                .uri("http://localhost"));
        routes.route("frontend", route -> route
                .path("/**")
                .filters(filters -> filters.removeRequestHeader(ApiHeaders.INTERNAL_KEY))
                .uri(frontend));
        return routes.build();
    }

    private static void addProxyRoute(RouteLocatorBuilder.Builder routes, String id, String upstream,
                                      ErrorCode unavailableCode, String... paths) {
        routes.route(id, route -> route
                .path(paths)
                .filters(filters -> filters
                        .removeRequestHeader(ApiHeaders.INTERNAL_KEY)
                        .filter(new GatewayProblemFilter(unavailableCode,
                                unavailableCode == ErrorCode.ASSISTANT_UNAVAILABLE
                                        ? "Assistant unavailable" : "Service unavailable")))
                .uri(upstream));
    }

    private static GatewayFilter problemResponse(ErrorCode code, String detail) {
        return (exchange, chain) -> GatewayProblemWriter.write(exchange, code, detail);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    WebFilter requestIdAndSecurityHeadersFilter(Environment environment) {
        boolean hstsEnabled = environment.getProperty("HSTS_ENABLED", Boolean.class, false);
        return (exchange, chain) -> {
            String requestId = exchange.getRequest().getHeaders().getFirst(ApiHeaders.REQUEST_ID);
            if (requestId == null || !REQUEST_ID_PATTERN.matcher(requestId).matches()) {
                requestId = UUID.randomUUID().toString();
            }
            String finalRequestId = requestId;
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .headers(headers -> headers.set(ApiHeaders.REQUEST_ID, finalRequestId))
                    .build();
            exchange.getAttributes().put(ApiHeaders.REQUEST_ID, finalRequestId);
            ServerHttpResponse response = exchange.getResponse();
            response.beforeCommit(() -> {
                HttpHeaders headers = response.getHeaders();
                headers.set(ApiHeaders.REQUEST_ID, finalRequestId);
                headers.set("X-Content-Type-Options", "nosniff");
                headers.set("X-Frame-Options", "DENY");
                headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
                headers.set("Permissions-Policy", "camera=(self), geolocation=()");
                headers.set("Content-Security-Policy", CONTENT_SECURITY_POLICY);
                headers.remove("Server");
                headers.remove("X-Powered-By");
                headers.remove("X-AspNet-Version");
                headers.remove("X-AspNetMvc-Version");
                if (hstsEnabled) {
                    headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
                } else {
                    headers.remove("Strict-Transport-Security");
                }
                return Mono.empty();
            });
            return chain.filter(exchange.mutate().request(request).build());
        };
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    WebFilter requestBodyLimitFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            long limit = isCourierStatusUpload(request) ? COURIER_STATUS_BODY_LIMIT : DEFAULT_BODY_LIMIT;
            if (request.getHeaders().getContentLength() > limit) {
                return GatewayProblemWriter.write(exchange, ErrorCode.PAYLOAD_TOO_LARGE, "Payload too large");
            }

            AtomicLong receivedBytes = new AtomicLong();
            ServerHttpRequestDecorator limitedRequest = new ServerHttpRequestDecorator(request) {
                @Override
                public Flux<DataBuffer> getBody() {
                    return super.getBody().handle((buffer, sink) -> {
                        long total = receivedBytes.addAndGet(buffer.readableByteCount());
                        if (total > limit) {
                            DataBufferUtils.release(buffer);
                            sink.error(new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE));
                        } else {
                            sink.next(buffer);
                        }
                    });
                }
            };
            return chain.filter(exchange.mutate().request(limitedRequest).build());
        };
    }

    private static boolean isCourierStatusUpload(ServerHttpRequest request) {
        return request.getMethod() == org.springframework.http.HttpMethod.POST
                && request.getURI().getPath().matches("/api/v1/courier/deliveries/[^/]+/status");
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    CorsWebFilter corsWebFilter(Environment environment) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.addAllowedOrigin(environment.getProperty("PUBLIC_BASE_URL", "http://localhost:8080"));
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            cors.addAllowedOrigin("http://localhost:5173");
        }
        cors.setAllowCredentials(false);
        cors.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Accept", "Authorization", "Content-Type", "Idempotency-Key",
                ApiHeaders.REQUEST_ID, ApiHeaders.REQUESTED_WITH));
        cors.setExposedHeaders(List.of(ApiHeaders.REQUEST_ID, ApiHeaders.RETRY_AFTER));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return new CorsWebFilter(source);
    }

    @Bean
    GlobalFilter rateLimitGlobalFilter(Environment environment) {
        return new RateLimitGlobalFilter(environment);
    }

    private static final class RateLimitGlobalFilter implements GlobalFilter, Ordered {

        private static final class Bucket {
            private final double refillPerSecond;
            private final double capacity;
            private double tokens;
            private long lastRefillNanos;

            private Bucket(double capacity, double refillPerSecond) {
                this.capacity = capacity;
                this.refillPerSecond = refillPerSecond;
                this.tokens = capacity;
                this.lastRefillNanos = System.nanoTime();
            }

            synchronized boolean tryConsume() {
                refill();
                if (tokens < 1.0d) {
                    return false;
                }
                tokens -= 1.0d;
                return true;
            }

            synchronized long retryAfterSeconds() {
                refill();
                return Math.max(1L, (long) Math.ceil((1.0d - tokens) / refillPerSecond));
            }

            private void refill() {
                long now = System.nanoTime();
                double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0d;
                if (elapsedSeconds > 0) {
                    tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
                    lastRefillNanos = now;
                }
            }
        }

        private final Environment environment;
        private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

        private RateLimitGlobalFilter(Environment environment) {
            this.environment = environment;
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            if (!environment.getProperty("RATE_LIMIT_ENABLED", Boolean.class, true)) {
                return chain.filter(exchange);
            }
            String path = exchange.getRequest().getURI().getPath();
            String group = rateLimitGroup(exchange.getRequest().getMethod(), path);
            if (group == null) {
                return chain.filter(exchange);
            }
            Bucket bucket = buckets.computeIfAbsent(resolveClientIp(exchange) + ':' + group,
                    key -> new Bucket(bucketCapacity(group), bucketRefillPerSecond(group)));
            if (!bucket.tryConsume()) {
                exchange.getResponse().getHeaders().set(ApiHeaders.RETRY_AFTER,
                        Long.toString(bucket.retryAfterSeconds()));
                return GatewayProblemWriter.write(exchange, ErrorCode.RATE_LIMITED, "Too many requests");
            }
            return chain.filter(exchange);
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE + 10;
        }

        private String resolveClientIp(ServerWebExchange exchange) {
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            if (remote == null) {
                return "unknown";
            }
            return remote.getAddress() == null ? remote.getHostString() : remote.getAddress().getHostAddress();
        }

        private String rateLimitGroup(org.springframework.http.HttpMethod method, String path) {
            if (method == org.springframework.http.HttpMethod.POST
                    && (pathIs(path, "/api/v1/auth/login") || pathIs(path, "/api/v1/auth/register")
                    || pathIs(path, "/api/v1/auth/register-merchant") || pathIs(path, "/api/v1/oauth/token"))) {
                return "auth-login";
            }
            if (pathIs(path, "/api/v1/auth/refresh")) {
                return "auth-refresh";
            }
            if (pathIs(path, "/api/v1/public/quotes") || path.startsWith("/api/v1/public/tracking/")) {
                return "public-tracking";
            }
            if (pathIs(path, "/mcp") || pathIs(path, "/api/v1/assistant")
                    || path.startsWith("/api/v1/assistant/")) {
                return "assistant";
            }
            if (path.equals("/api") || path.startsWith("/api/")) {
                return "default-api";
            }
            return null;
        }

        private static boolean pathIs(String path, String expected) {
            return path.equals(expected);
        }

        private double bucketCapacity(String group) {
            return switch (group) {
                case "auth-login", "auth-refresh" -> 30.0d;
                case "public-tracking" -> 40.0d;
                case "assistant" -> 5.0d;
                case "default-api" -> 100.0d;
                default -> 10.0d;
            };
        }

        private double bucketRefillPerSecond(String group) {
            return switch (group) {
                case "auth-login" -> 0.5d;
                case "auth-refresh" -> 1.0d;
                case "public-tracking" -> 2.0d;
                case "assistant" -> 10.0d / 60.0d;
                case "default-api" -> 5.0d;
                default -> 1.0d;
            };
        }
    }
}

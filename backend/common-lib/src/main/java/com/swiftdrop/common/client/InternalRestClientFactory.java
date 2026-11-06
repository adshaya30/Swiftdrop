package com.swiftdrop.common.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.error.FieldError;
import com.swiftdrop.common.http.ApiHeaders;
import com.swiftdrop.common.security.InternalApiKey;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

/**
 * Builds RestClients for internal service-to-service calls (SRS 3.3, 7.4): 5 s connect and 10 s read timeouts,
 * X-Internal-Key, X-Request-Id propagation. A callee business error is rethrown with the same code, status and
 * detail; connection failures, timeouts and 5xx become 503 SERVICE_UNAVAILABLE.
 */
public class InternalRestClientFactory {

    private static final Set<String> STANDARD_MEMBERS =
            Set.of("type", "title", "status", "detail", "code", "traceId", "timestamp", "errors");

    private final InternalApiKey key;
    private final ObjectMapper mapper;

    public InternalRestClientFactory(InternalApiKey key, ObjectMapper mapper) {
        this.key = key;
        this.mapper = mapper;
    }

    public RestClient create(String baseUrl) {
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.jdk()
                .withHttpClientCustomizer(b -> b.version(HttpClient.Version.HTTP_1_1))
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(Duration.ofSeconds(5))
                        .withReadTimeout(Duration.ofSeconds(10)));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor(this::intercept)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw translate(response);
                })
                .build();
    }

    private ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {
        request.getHeaders().set(ApiHeaders.INTERNAL_KEY, key.value());
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            request.getHeaders().set(ApiHeaders.REQUEST_ID, requestId);
        }
        try {
            return execution.execute(request, body);
        } catch (IOException e) {
            throw unavailable();
        }
    }

    private DomainException translate(ClientHttpResponse response) {
        try {
            if (response.getStatusCode().is5xxServerError()) {
                return unavailable();
            }
            JsonNode node = mapper.readTree(response.getBody());
            ErrorCode code = ErrorCode.valueOf(node.path("code").asText());
            List<FieldError> errors = new ArrayList<>();
            node.path("errors").forEach(e -> errors.add(new FieldError(e.path("field").asText(), e.path("message").asText())));
            Map<String, Object> extensions = new LinkedHashMap<>();
            node.fields().forEachRemaining(f -> {
                if (!STANDARD_MEMBERS.contains(f.getKey())) {
                    extensions.put(f.getKey(), mapper.convertValue(f.getValue(), Object.class));
                }
            });
            return new DomainException(code, node.path("detail").asText(code.title()), errors, extensions);
        } catch (IOException | RuntimeException e) {
            return unavailable();
        }
    }

    private static DomainException unavailable() {
        return new DomainException(ErrorCode.SERVICE_UNAVAILABLE, "A dependent service is unavailable");
    }
}

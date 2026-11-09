package com.swiftdrop.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftdrop.auth.exception.DeliveryServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.UUID;

/**
 * Internal REST client for communicating with delivery-service.
 * SRS v2.1 – Flow 4.6, FR-AUTH-020
 */
@Component
public class DeliveryServiceClient {

    private static final Logger log = LoggerFactory.getLogger(DeliveryServiceClient.class);

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Key";

    private final RestTemplate restTemplate;
    private final String deliveryServiceUrl;
    private final String internalApiKey;

    public DeliveryServiceClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${delivery.service.url:${DELIVERY_SERVICE_URL:http://localhost:8084}}") String deliveryServiceUrl,
            @Value("${internal.api.key:${INTERNAL_API_KEY:swiftdrop-internal-secret-key}}") String internalApiKey) {
        this.deliveryServiceUrl = deliveryServiceUrl.endsWith("/") ? deliveryServiceUrl.substring(0, deliveryServiceUrl.length() - 1) : deliveryServiceUrl;
        this.internalApiKey = internalApiKey;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }

    public record ActiveDeliveriesResponse(@JsonProperty("inProgress") int inProgress) {}

    /**
     * Queries delivery-service for the count of active/in-progress deliveries assigned to a courier.
     *
     * @param courierId the ID of the courier profile or courier user
     * @return the number of active deliveries in progress
     */
    public int getActiveDeliveriesCount(UUID courierId) {
        String url = deliveryServiceUrl + "/internal/v1/couriers/" + courierId + "/active-count";

        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_KEY_HEADER, internalApiKey);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            log.debug("Checking active deliveries for courier {} at {}", courierId, url);
            ResponseEntity<ActiveDeliveriesResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    ActiveDeliveriesResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody().inProgress();
            }
            return 0;
        } catch (HttpClientErrorException.NotFound notFound) {
            log.debug("Courier {} not found in delivery-service; assuming 0 active deliveries", courierId);
            return 0;
        } catch (ResourceAccessException | HttpServerErrorException | HttpClientErrorException e) {
            log.error("Failed to query active delivery count for courier {} from {}: {}", courierId, url, e.getMessage());
            throw new DeliveryServiceUnavailableException("Delivery service is unavailable or returned an error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error querying delivery service for courier {}: {}", courierId, e.getMessage());
            throw new DeliveryServiceUnavailableException("Delivery service communication failed: " + e.getMessage(), e);
        }
    }
}

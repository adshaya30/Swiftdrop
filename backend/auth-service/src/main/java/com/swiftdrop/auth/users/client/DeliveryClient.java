package com.swiftdrop.auth.users.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.swiftdrop.auth.exception.DeliveryServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Internal REST client for communicating with delivery-service.
 * SRS v2.1 – Flow 5, Section 7.4, FR-AUTH-020
 * // TODO(common): replace manual client construction with InternalRestClientFactory when published.
 */
@Component
public class DeliveryClient {

    private static final Logger log = LoggerFactory.getLogger(DeliveryClient.class);
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Key";

    private final RestClient restClient;
    private final String deliveryServiceUrl;
    private final String internalApiKey;

    public DeliveryClient(
            @Value("${delivery.service.url:${DELIVERY_SERVICE_URL:http://localhost:8084}}") String deliveryServiceUrl,
            @Value("${internal.api.key:${INTERNAL_API_KEY:swiftdrop-internal-secret-key}}") String internalApiKey) {
        this.deliveryServiceUrl = deliveryServiceUrl.endsWith("/") ? deliveryServiceUrl.substring(0, deliveryServiceUrl.length() - 1) : deliveryServiceUrl;
        this.internalApiKey = internalApiKey;
        this.restClient = RestClient.builder().build();
    }

    public record ActiveDeliveriesResponse(@JsonProperty("inProgress") int inProgress) {}

    /**
     * Queries delivery-service for the count of active deliveries assigned to a courier.
     *
     * @param courierId the UUID of the courier
     * @return the number of active deliveries in progress
     */
    public int getActiveCount(UUID courierId) {
        String url = deliveryServiceUrl + "/internal/v1/couriers/" + courierId + "/active-count";

        try {
            log.debug("Checking active deliveries for courier {} at {}", courierId, url);
            ActiveDeliveriesResponse response = restClient.get()
                    .uri(url)
                    .header(INTERNAL_KEY_HEADER, internalApiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ActiveDeliveriesResponse.class);

            return response != null ? response.inProgress() : 0;
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

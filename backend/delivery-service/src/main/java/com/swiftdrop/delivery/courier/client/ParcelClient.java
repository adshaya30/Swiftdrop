package com.swiftdrop.delivery.courier.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.client.InternalRestClientFactory;
import com.swiftdrop.common.security.InternalApiKey;
import com.swiftdrop.delivery.courier.dto.ParcelInternalDetailDto;
import com.swiftdrop.delivery.courier.dto.ParcelTransitionRequest;
import com.swiftdrop.delivery.courier.dto.ParcelTransitionResponse;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ParcelClient {

    private final RestClient restClient;

    public ParcelClient(
            InternalApiKey internalApiKey,
            ObjectMapper objectMapper,
            @Value("${swiftdrop.parcel-service-url:http://parcel-service:8082}") String parcelServiceUrl) {
        this.restClient = new InternalRestClientFactory(internalApiKey, objectMapper).create(parcelServiceUrl);
    }

    public ParcelInternalDetailDto getParcel(UUID parcelId) {
        return restClient.get()
                .uri("/internal/v1/parcels/{id}", parcelId)
                .retrieve()
                .body(ParcelInternalDetailDto.class);
    }

    public ParcelTransitionResponse transition(UUID parcelId, ParcelTransitionRequest request) {
        return restClient.post()
                .uri("/internal/v1/parcels/{id}/transition", parcelId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ParcelTransitionResponse.class);
    }
}


package com.swiftdrop.delivery.domain;

import com.swiftdrop.common.enums.ParcelStatus;
import java.time.Instant;
import java.util.UUID;

public record DeliveryListItem(
        UUID parcelId,
        String trackingNumber,
        ParcelStatus status,
        String pickupCity,
        String dropoffCity,
        String originDistrictName,
        String destinationDistrictName,
        Instant assignedAt) {
}
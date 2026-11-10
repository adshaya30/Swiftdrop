package com.swiftdrop.delivery.assignment.dto;

import com.swiftdrop.common.enums.ParcelStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminDeliveryListItem(
        UUID parcelId,
        String trackingNumber,
        ParcelStatus status,
        UUID courierId,
        Instant assignedAt,
        String originDistrictCode,
        String originDistrictName,
        String destinationDistrictCode,
        String destinationDistrictName,
        String pickupCity,
        String dropoffCity,
        Instant parcelCreatedAt) {
}
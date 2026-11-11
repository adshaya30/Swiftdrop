package com.swiftdrop.delivery.courier.dto;

import com.swiftdrop.common.enums.ParcelStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CourierDeliveryDetailResponse(
        UUID id,
        String trackingNumber,
        ParcelStatus status,
        Instant createdAt,
        Instant updatedAt,
        AddressContactDto pickup,
        AddressContactDto dropoff,
        ParcelSpecDto parcel,
        String deliveryInstructions,
        ZoneInfoDto originZone,
        ZoneInfoDto destinationZone,
        String merchantReference,
        List<ParcelStatus> allowedNextStatuses,
        List<FailureReasonDto> allowedFailureReasons,
        DeliveryProofDto proof) {
}


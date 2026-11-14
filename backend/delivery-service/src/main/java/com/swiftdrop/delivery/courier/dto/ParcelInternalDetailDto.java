package com.swiftdrop.delivery.courier.dto;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import java.time.Instant;
import java.util.UUID;

public record ParcelInternalDetailDto(
        UUID id,
        String trackingNumber,
        ParcelStatus status,
        int statusSequence,
        Instant createdAt,
        Instant updatedAt,
        Instant terminalAt,
        AddressContactDto pickup,
        AddressContactDto dropoff,
        ParcelSpecDto parcel,
        String deliveryInstructions,
        ZoneInfoDto originZone,
        ZoneInfoDto destinationZone,
        String merchantReference,
        OwnerType ownerType,
        UUID customerId,
        UUID merchantId) {
}


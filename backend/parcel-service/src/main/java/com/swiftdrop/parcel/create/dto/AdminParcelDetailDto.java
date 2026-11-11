package com.swiftdrop.parcel.create.dto;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import java.time.Instant;
import java.util.UUID;

/** Master 8.1: customer detail fields plus both owner identifiers; authorization belongs to the caller. */
public record AdminParcelDetailDto(UUID id, String trackingNumber, ParcelStatus status, OwnerType ownerType,
        String merchantReference, ParcelDetailDto.Pickup pickup, ParcelDetailDto.Dropoff dropoff,
        ParcelDetailDto.ParcelData parcel, String deliveryInstructions,
        ParcelDetailDto.Zone originZone, ParcelDetailDto.Zone destinationZone,
        CreatedParcel.Fee fee, Instant createdAt, Instant updatedAt,
        UUID customerId, UUID merchantId) { }

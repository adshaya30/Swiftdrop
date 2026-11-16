package com.swiftdrop.parcel.create.dto;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import java.time.Instant;
import java.util.UUID;

/** SRS 7.4 / Master 8.1: admin detail fields plus the authoritative status sequence. */
public record InternalParcelDetailDto(UUID id, String trackingNumber, ParcelStatus status, OwnerType ownerType,
        String merchantReference, ParcelDetailDto.Pickup pickup, ParcelDetailDto.Dropoff dropoff,
        ParcelDetailDto.ParcelData parcel, String deliveryInstructions,
        ParcelDetailDto.Zone originZone, ParcelDetailDto.Zone destinationZone,
        CreatedParcel.Fee fee, Instant createdAt, Instant updatedAt,
        UUID customerId, UUID merchantId, int statusSequence) { }

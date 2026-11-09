package com.swiftdrop.common.events.data;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Data of {@code parcel.created} (SRS 3.5). Nullable: customerId, merchantId, merchantReference.
 * No customer, merchant or recipient contact data (BR-28).
 */
public record ParcelCreatedData(
        UUID parcelId,
        String trackingNumber,
        ParcelStatus status,
        OwnerType ownerType,
        UUID customerId,
        UUID merchantId,
        String merchantReference,
        String originDistrictCode,
        String originDistrictName,
        String originZoneName,
        String destinationDistrictCode,
        String destinationDistrictName,
        String destinationZoneName,
        String pickupCity,
        String dropoffCity,
        Instant createdAt) {
}

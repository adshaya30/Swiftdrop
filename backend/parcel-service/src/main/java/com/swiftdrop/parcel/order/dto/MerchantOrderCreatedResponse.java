package com.swiftdrop.parcel.order.dto;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.parcel.create.dto.CreatedParcel;
import java.time.Instant;
import java.util.UUID;

/** FR-PAR-007: 201 body {id, trackingNumber, status, merchantReference, fee, trackingUrl, createdAt}. */
public record MerchantOrderCreatedResponse(UUID id, String trackingNumber, ParcelStatus status,
                                           String merchantReference, CreatedParcel.Fee fee, String trackingUrl,
                                           Instant createdAt) {

    /** trackingUrl = PUBLIC_BASE_URL + /track/{trackingNumber} (FR-PAR-007, SRS 9.2). */
    public static MerchantOrderCreatedResponse from(CreatedParcel parcel, String publicBaseUrl) {
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return new MerchantOrderCreatedResponse(parcel.id(), parcel.trackingNumber(), parcel.status(),
                parcel.merchantReference(), parcel.fee(), base + "/track/" + parcel.trackingNumber(),
                parcel.createdAt());
    }
}

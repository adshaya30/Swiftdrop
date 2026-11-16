package com.swiftdrop.parcel.create.dto;

import com.swiftdrop.common.enums.ParcelStatus;
import java.time.Instant;
import java.util.UUID;

/** FR-PAR-005 customer response, deliberately excludes merchant-only fields. */
public record CustomerParcelCreatedResponse(UUID id, String trackingNumber, ParcelStatus status,
                                            CreatedParcel.Fee fee, Instant createdAt) {
    public static CustomerParcelCreatedResponse from(CreatedParcel parcel) {
        return new CustomerParcelCreatedResponse(parcel.id(), parcel.trackingNumber(),
                parcel.status(), parcel.fee(), parcel.createdAt());
    }
}

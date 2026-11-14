package com.swiftdrop.parcel.lifecycle.dto;

import com.swiftdrop.common.enums.ParcelStatus;
import java.time.Instant;
import java.util.UUID;

/** Master 8.1: cancel endpoints answer 200 {id, trackingNumber, status, statusChangedAt}. */
public record AdminParcelActionResponse(UUID id, String trackingNumber, ParcelStatus status, Instant statusChangedAt) {

    public static AdminParcelActionResponse from(TransitionResult result) {
        return new AdminParcelActionResponse(result.parcelId(), result.trackingNumber(), result.status(),
                result.statusChangedAt());
    }
}

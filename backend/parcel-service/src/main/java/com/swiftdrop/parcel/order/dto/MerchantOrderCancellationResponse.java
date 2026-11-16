package com.swiftdrop.parcel.order.dto;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.parcel.lifecycle.dto.TransitionResult;
import java.time.Instant;
import java.util.UUID;

/** Master 8.1: cancel endpoints answer 200 {id, trackingNumber, status, statusChangedAt}. */
public record MerchantOrderCancellationResponse(UUID id, String trackingNumber, ParcelStatus status,
                                                Instant statusChangedAt) {

    public static MerchantOrderCancellationResponse from(TransitionResult result) {
        return new MerchantOrderCancellationResponse(result.parcelId(), result.trackingNumber(), result.status(),
                result.statusChangedAt());
    }
}

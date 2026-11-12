package com.swiftdrop.parcel.order.dto;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.parcel.lifecycle.dto.TransitionResult;
import java.time.Instant;
import java.util.UUID;

/** FR-PAR-016: {parcelId, status, sequence, statusChangedAt}. */
public record InternalTransitionResponse(UUID parcelId, ParcelStatus status, int sequence, Instant statusChangedAt) {

    public static InternalTransitionResponse from(TransitionResult result) {
        return new InternalTransitionResponse(result.parcelId(), result.status(), result.sequence(),
                result.statusChangedAt());
    }
}

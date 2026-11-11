package com.swiftdrop.parcel.lifecycle.dto;

import com.swiftdrop.common.enums.ParcelStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Result returned by all {@code ParcelTransitionService} methods.
 * Master 8.1: {@code TransitionResult(parcelId, trackingNumber, status, sequence, statusChangedAt)}.
 */
public record TransitionResult(
        UUID parcelId,
        String trackingNumber,
        ParcelStatus status,
        int sequence,
        Instant statusChangedAt) {
}


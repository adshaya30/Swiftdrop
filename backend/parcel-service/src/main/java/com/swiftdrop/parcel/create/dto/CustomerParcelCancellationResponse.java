package com.swiftdrop.parcel.create.dto;

import com.swiftdrop.common.enums.ParcelStatus;
import java.time.Instant;
import java.util.UUID;

/** FR-PAR-009: customer view of the authoritative lifecycle result. */
public record CustomerParcelCancellationResponse(UUID id, String trackingNumber,
        ParcelStatus status, Instant statusChangedAt) {}

package com.swiftdrop.delivery.courier.dto;

import com.swiftdrop.common.enums.ParcelStatus;
import java.time.Instant;
import java.util.UUID;

public record ParcelStatusUpdateResponse(
        UUID parcelId,
        ParcelStatus status,
        int sequence,
        Instant statusChangedAt) {
}


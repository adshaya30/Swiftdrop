package com.swiftdrop.common.events.data;

import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.enums.FailureReason;
import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Data of {@code parcel.status.changed} (SRS 3.5). Nullable: actorUserId (null for SYSTEM), note, failureReasonCode,
 * customerId, merchantId, merchantReference. The free-text staff note travels only here (BR-28): never log it.
 */
public record ParcelStatusChangedData(
        UUID parcelId,
        String trackingNumber,
        int sequence,
        ParcelStatus previousStatus,
        ParcelStatus status,
        Instant statusChangedAt,
        UUID actorUserId,
        ActorRole actorRole,
        String note,
        FailureReason failureReasonCode,
        OwnerType ownerType,
        UUID customerId,
        UUID merchantId,
        String merchantReference) {

    /** Leaves out the free-text note so it never reaches a log line (BR-28). */
    @Override
    public String toString() {
        return "ParcelStatusChangedData[parcelId=" + parcelId + ", trackingNumber=" + trackingNumber
                + ", sequence=" + sequence + ", previousStatus=" + previousStatus + ", status=" + status + "]";
    }
}

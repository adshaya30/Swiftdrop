package com.swiftdrop.parcel.order.dto;

import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.enums.FailureReason;
import com.swiftdrop.common.enums.ParcelStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * FR-PAR-016 body of POST /internal/v1/parcels/{id}/transition. actorRole is COURIER or ADMIN (the caller,
 * delivery-service, has already checked the assignment). The lifecycle service validates the rest (Table 2.3, 2.4).
 */
public record InternalTransitionRequest(
        @NotNull ParcelStatus toStatus,
        @NotNull UUID actorUserId,
        @NotNull ActorRole actorRole,
        @Size(max = 500) String note,
        FailureReason failureReason,
        @Size(max = 100) String recipientName) {
}

package com.swiftdrop.parcel.lifecycle.dto;

import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.enums.FailureReason;
import com.swiftdrop.common.enums.ParcelStatus;
import java.util.UUID;

/**
 * Command carrying transition data for {@code ParcelTransitionService.transition}.
 * FR-PAR-002: actorRole, toStatus, note, failureReason, recipientName.
 */
public record TransitionCommand(
        UUID parcelId,
        ParcelStatus toStatus,
        UUID actorUserId,
        ActorRole actorRole,
        String note,
        FailureReason failureReason,
        String recipientName) {

    public TransitionCommand {
        if (parcelId == null) {
            throw new IllegalArgumentException("parcelId must not be null");
        }
        if (toStatus == null) {
            throw new IllegalArgumentException("toStatus must not be null");
        }
        if (actorRole == null) {
            throw new IllegalArgumentException("actorRole must not be null");
        }
    }
}


package com.swiftdrop.delivery.courier.dto;

import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.enums.FailureReason;
import com.swiftdrop.common.enums.ParcelStatus;
import java.util.UUID;

public record ParcelTransitionRequest(
        ParcelStatus toStatus,
        UUID actorUserId,
        ActorRole actorRole,
        String note,
        FailureReason failureReason,
        String recipientName) {
}


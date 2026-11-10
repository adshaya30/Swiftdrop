package com.swiftdrop.common.events.data;

import java.time.Instant;
import java.util.UUID;

/** Data of {@code delivery.unassigned} (SRS 3.5). */
public record DeliveryUnassignedData(
        UUID parcelId,
        Instant unassignedAt,
        String reason) {

    /** The only reason SRS 3.5 defines. */
    public static final String REASON_COURIER_DEACTIVATED = "COURIER_DEACTIVATED";
}

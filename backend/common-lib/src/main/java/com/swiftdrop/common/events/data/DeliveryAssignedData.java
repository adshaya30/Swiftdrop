package com.swiftdrop.common.events.data;

import java.time.Instant;
import java.util.UUID;

/** Data of {@code delivery.assigned} (SRS 3.5). The courier's first name is allowed staff data (BR-28). */
public record DeliveryAssignedData(
        UUID parcelId,
        UUID courierId,
        String courierFirstName,
        Instant assignedAt) {
}

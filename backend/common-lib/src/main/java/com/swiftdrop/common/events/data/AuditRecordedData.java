package com.swiftdrop.common.events.data;

import com.swiftdrop.common.enums.ActorRole;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Data of {@code audit.recorded} (SRS 3.5). Nullable: actorUserId (system actors). details is a JSON object without
 * passwords, secrets or contact data. Write it through {@link com.swiftdrop.common.events.AuditEvents}.
 */
public record AuditRecordedData(
        String service,
        UUID actorUserId,
        ActorRole actorRole,
        String action,
        String entityType,
        String entityId,
        Map<String, Object> details,
        Instant occurredAt) {
}

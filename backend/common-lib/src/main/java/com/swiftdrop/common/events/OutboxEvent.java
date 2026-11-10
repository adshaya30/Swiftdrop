package com.swiftdrop.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the shared {@code outbox_events} table (SRS 6.1): id (= eventId), event_type, routing_key, payload
 * (JSONB, the full envelope), correlation_id, created_at, published_at (null until confirmed), attempts.
 *
 * <p>Deliberately a plain record stored through JDBC, not a JPA entity: an auto-registered entity would break
 * {@code ddl-auto=validate} in tracking-service and notification-service, which have no outbox table (SRS 6.1).
 */
public record OutboxEvent(
        UUID id,
        String eventType,
        String routingKey,
        String payload,
        UUID correlationId,
        Instant createdAt,
        Instant publishedAt,
        int attempts) {
}

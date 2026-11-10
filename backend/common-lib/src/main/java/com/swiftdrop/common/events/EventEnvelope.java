package com.swiftdrop.common.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The JSON envelope of every event (SRS 3.5):
 * {@code {eventId, eventType, schemaVersion, occurredAt, correlationId, data}}.
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        UUID correlationId,
        T data) {

    public static final int SCHEMA_VERSION = 1;

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(data, "data");
    }

    /** A new envelope with a random event id and schema version 1. */
    public static <T> EventEnvelope<T> create(String eventType, Instant occurredAt, UUID correlationId, T data) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, SCHEMA_VERSION, occurredAt, correlationId, data);
    }
}

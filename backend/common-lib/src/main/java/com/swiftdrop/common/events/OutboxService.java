package com.swiftdrop.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.web.RequestIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Writes an event to {@code outbox_events} in the caller's transaction (transactional outbox, SRS 3.5, D-05). The
 * {@link OutboxPublisher} sends it to RabbitMQ after the commit, so a down broker never fails a business operation
 * (NFR-REL-001).
 */
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Adds an event. {@code routingKey} is one of {@link EventTypes}; {@code data} is the matching data record.
     * Must run inside an existing transaction.
     *
     * @return the envelope that was stored
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> EventEnvelope<T> add(String routingKey, T data) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException(
                    "OutboxService.add must run inside the transaction of the business change");
        }
        Class<?> expected = EventTypes.dataType(routingKey);
        Objects.requireNonNull(data, "data");
        if (!expected.isInstance(data)) {
            throw new IllegalArgumentException(routingKey + " carries " + expected.getSimpleName()
                    + ", not " + data.getClass().getSimpleName());
        }
        Instant now = clock.instant();
        EventEnvelope<T> envelope = EventEnvelope.create(routingKey, now, correlationId(), data);
        repository.insert(new OutboxEvent(envelope.eventId(), routingKey, routingKey, toJson(envelope),
                envelope.correlationId(), now, null, 0));
        return envelope;
    }

    /** NFR-OBS-002: the request id of the originating request when it is a UUID, otherwise a fresh one. */
    static UUID correlationId() {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            try {
                return UUID.fromString(requestId);
            } catch (IllegalArgumentException ignored) {
                // not a UUID: fall through to a fresh correlation id
            }
        }
        return UUID.randomUUID();
    }

    private String toJson(EventEnvelope<?> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Event " + envelope.eventType() + " could not be serialised", e);
        }
    }
}

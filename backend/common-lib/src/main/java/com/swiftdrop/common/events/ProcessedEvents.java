package com.swiftdrop.common.events;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Event de-duplication through the shared {@code processed_events} table (SRS 3.5, 6.1), for delivery-service and
 * tracking-service only: notification-service relies on its own unique keys (FR-NTF-004, FR-WHK-006).
 *
 * <p>Stored through JDBC rather than a JPA entity, for the same reason as {@link OutboxEvent}: services without this
 * table must keep passing {@code ddl-auto=validate}.
 */
public class ProcessedEvents {

    // ON CONFLICT rather than catching a duplicate-key error: in PostgreSQL that error would abort the transaction.
    private static final String INSERT =
            "INSERT INTO processed_events (event_id, processed_at) VALUES (?, ?) ON CONFLICT (event_id) DO NOTHING";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ProcessedEvents(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Records the event in the caller's transaction, so it rolls back with a failed handling.
     *
     * @return true the first time; false if the event was already processed (acknowledge it and skip the handling)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException(
                    "ProcessedEvents.firstTime must run inside the transaction of the event handling");
        }
        return jdbc.update(INSERT, eventId, clock.instant().atOffset(ZoneOffset.UTC)) == 1;
    }
}

package com.swiftdrop.common.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Tag("SRS-3.5")
class ProcessedEventsTest {

    private final SeenEventsJdbcTemplate jdbc = new SeenEventsJdbcTemplate();
    private final ProcessedEvents processed = new ProcessedEvents(jdbc, Clock.systemUTC());

    @BeforeEach
    void inTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void cleanUp() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("SRS 3.5: firstTime is true for a new event id and false for one already processed")
    void firstTimeThenDuplicate() {
        UUID eventId = UUID.randomUUID();

        assertThat(processed.firstTime(eventId)).isTrue();
        assertThat(processed.firstTime(eventId)).isFalse();
        assertThat(processed.firstTime(UUID.randomUUID())).isTrue();
    }

    @Test
    @DisplayName("SRS 3.5: a duplicate must not raise a database error, which would abort the caller's transaction")
    void usesOnConflict() {
        processed.firstTime(UUID.randomUUID());

        assertThat(jdbc.statements).singleElement().asString()
                .contains("INSERT INTO processed_events")
                .contains("ON CONFLICT (event_id) DO NOTHING");
    }

    @Test
    @DisplayName("SRS 3.5: firstTime refuses to run outside the caller's transaction")
    void requiresTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);

        assertThatThrownBy(() -> processed.firstTime(UUID.randomUUID()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbc.statements).isEmpty();
    }

    @Test
    @DisplayName("SRS 3.5: an event id is required")
    void requiresEventId() {
        assertThatThrownBy(() -> processed.firstTime(null)).isInstanceOf(NullPointerException.class);
    }
}

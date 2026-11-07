package com.swiftdrop.common.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.enums.UserStatus;
import com.swiftdrop.common.enums.VehicleType;
import com.swiftdrop.common.events.data.CourierChangedData;
import com.swiftdrop.common.events.data.DeliveryAssignedData;
import com.swiftdrop.common.web.RequestIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Tag("SRS-3.5")
class OutboxServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    private final InMemoryOutboxEventRepository repository = new InMemoryOutboxEventRepository();
    private final ObjectMapper mapper = EventsAutoConfiguration.defaultMapper();
    private final OutboxService service = new OutboxService(repository, mapper, Clock.fixed(NOW, ZoneOffset.UTC));

    private final CourierChangedData courier = new CourierChangedData(UUID.randomUUID(), "Kamal Perera",
            UserStatus.ACTIVE, VehicleType.MOTORBIKE, "WP BAA-1234");

    @BeforeEach
    void inTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void cleanUp() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        MDC.clear();
    }

    @Test
    @DisplayName("SRS 3.5 D-05: add writes one row whose payload is the full envelope and whose id is the event id")
    void writesRow() throws Exception {
        EventEnvelope<CourierChangedData> envelope = service.add(EventTypes.COURIER_CHANGED, courier);

        assertThat(repository.rows).hasSize(1);
        OutboxEvent row = repository.rows.get(0);
        assertThat(row.id()).isEqualTo(envelope.eventId());
        assertThat(row.eventType()).isEqualTo("courier.changed");
        assertThat(row.routingKey()).isEqualTo("courier.changed");
        assertThat(row.correlationId()).isEqualTo(envelope.correlationId());
        assertThat(row.createdAt()).isEqualTo(NOW);
        assertThat(row.publishedAt()).isNull();
        assertThat(row.attempts()).isZero();

        JsonNode payload = mapper.readTree(row.payload());
        assertThat(payload.get("eventId").asText()).isEqualTo(envelope.eventId().toString());
        assertThat(payload.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(payload.get("occurredAt").asText()).isEqualTo("2026-11-07T10:00:00Z");
        assertThat(payload.get("data").get("vehicleType").asText()).isEqualTo("MOTORBIKE");
    }

    @Test
    @DisplayName("SRS 3.5: add refuses to run without a transaction (Propagation.MANDATORY)")
    void requiresTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);

        assertThatThrownBy(() -> service.add(EventTypes.COURIER_CHANGED, courier))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(repository.rows).isEmpty();
    }

    @Test
    @DisplayName("NFR-OBS-002: the correlation id is the request id from the MDC when it is a UUID")
    void correlationFromRequestId() {
        UUID requestId = UUID.randomUUID();
        MDC.put(RequestIdFilter.MDC_KEY, requestId.toString());

        assertThat(service.add(EventTypes.COURIER_CHANGED, courier).correlationId()).isEqualTo(requestId);
    }

    @Test
    @DisplayName("NFR-OBS-002: a request id that is not a UUID, or none, gives a fresh correlation id")
    void freshCorrelation() {
        MDC.put(RequestIdFilter.MDC_KEY, "not-a-uuid");
        UUID first = service.add(EventTypes.COURIER_CHANGED, courier).correlationId();
        MDC.clear();
        UUID second = service.add(EventTypes.COURIER_CHANGED, courier).correlationId();

        assertThat(first).isNotNull().isNotEqualTo(second);
    }

    @Test
    @DisplayName("SRS 3.5: unknown event types and data of the wrong type are rejected (no invented events)")
    void rejectsUnknownOrMismatched() {
        assertThatThrownBy(() -> service.add("parcel.deleted", courier))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.add(EventTypes.DELIVERY_ASSIGNED, courier))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(DeliveryAssignedData.class.getSimpleName());
        assertThatThrownBy(() -> service.add(EventTypes.COURIER_CHANGED, null))
                .isInstanceOf(NullPointerException.class);
        assertThat(repository.rows).isEmpty();
    }

    @Test
    @DisplayName("SRS 3.5: every event gets its own random event id")
    void uniqueIds() {
        UUID a = service.add(EventTypes.COURIER_CHANGED, courier).eventId();
        UUID b = service.add(EventTypes.COURIER_CHANGED, courier).eventId();
        assertThat(a).isNotEqualTo(b);
    }
}

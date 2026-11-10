package com.swiftdrop.common.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.enums.FailureReason;
import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.events.data.AuditRecordedData;
import com.swiftdrop.common.events.data.CourierChangedData;
import com.swiftdrop.common.events.data.DeliveryAssignedData;
import com.swiftdrop.common.events.data.DeliveryUnassignedData;
import com.swiftdrop.common.events.data.MerchantStatusChangedData;
import com.swiftdrop.common.events.data.ParcelCreatedData;
import com.swiftdrop.common.events.data.ParcelStatusChangedData;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SRS-3.5")
class EventEnvelopeJsonTest {

    private final ObjectMapper mapper = EventsAutoConfiguration.defaultMapper();

    @Test
    @DisplayName("SRS 3.5: the envelope serialises as {eventId, eventType, schemaVersion 1, occurredAt ISO UTC, correlationId, data}")
    void envelopeShape() throws Exception {
        UUID correlation = UUID.randomUUID();
        Instant at = Instant.parse("2026-10-20T08:15:30.123Z");
        ParcelStatusChangedData data = new ParcelStatusChangedData(UUID.randomUUID(), "SD-7K3M9QX2TA", 3,
                ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, at, UUID.randomUUID(), ActorRole.COURIER, null,
                null, OwnerType.MERCHANT, null, UUID.randomUUID(), "ORD-1001");
        EventEnvelope<ParcelStatusChangedData> envelope =
                EventEnvelope.create(EventTypes.PARCEL_STATUS_CHANGED, at, correlation, data);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(envelope));

        assertThat(fieldNames(json)).containsExactly(
                "eventId", "eventType", "schemaVersion", "occurredAt", "correlationId", "data");
        assertThat(json.get("eventType").asText()).isEqualTo("parcel.status.changed");
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-10-20T08:15:30.123Z");
        assertThat(json.get("correlationId").asText()).isEqualTo(correlation.toString());
        assertThat(json.get("data").get("status").asText()).isEqualTo("IN_TRANSIT");
        assertThat(json.get("data").get("sequence").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("SRS 3.5: an envelope round-trips through JSON with its typed data")
    void roundTrip() throws Exception {
        DeliveryAssignedData data = new DeliveryAssignedData(UUID.randomUUID(), UUID.randomUUID(), "Nimal",
                Instant.parse("2026-10-20T08:00:00Z"));
        EventEnvelope<DeliveryAssignedData> envelope =
                EventEnvelope.create(EventTypes.DELIVERY_ASSIGNED, Instant.now(), UUID.randomUUID(), data);

        EventEnvelope<DeliveryAssignedData> back = mapper.readValue(mapper.writeValueAsString(envelope),
                new TypeReference<EventEnvelope<DeliveryAssignedData>>() { });

        assertThat(back).isEqualTo(envelope);
    }

    @Test
    @DisplayName("SRS 3.5: every data record has exactly the fields listed in the event table")
    void dataRecordFields() {
        assertFields(ParcelCreatedData.class, "parcelId", "trackingNumber", "status", "ownerType", "customerId",
                "merchantId", "merchantReference", "originDistrictCode", "originDistrictName", "originZoneName",
                "destinationDistrictCode", "destinationDistrictName", "destinationZoneName", "pickupCity",
                "dropoffCity", "createdAt");
        assertFields(ParcelStatusChangedData.class, "parcelId", "trackingNumber", "sequence", "previousStatus",
                "status", "statusChangedAt", "actorUserId", "actorRole", "note", "failureReasonCode", "ownerType",
                "customerId", "merchantId", "merchantReference");
        assertFields(DeliveryAssignedData.class, "parcelId", "courierId", "courierFirstName", "assignedAt");
        assertFields(DeliveryUnassignedData.class, "parcelId", "unassignedAt", "reason");
        assertFields(CourierChangedData.class, "courierId", "fullName", "status", "vehicleType", "vehicleNumber");
        assertFields(MerchantStatusChangedData.class, "merchantId", "businessName", "previousStatus", "status",
                "reason");
        assertFields(AuditRecordedData.class, "service", "actorUserId", "actorRole", "action", "entityType",
                "entityId", "details", "occurredAt");
    }

    @Test
    @DisplayName("SRS 3.5: event types equal the routing keys and map to their data records")
    void eventTypes() {
        assertThat(EventTypes.ALL).containsExactlyInAnyOrder("parcel.created", "parcel.status.changed",
                "delivery.assigned", "delivery.unassigned", "courier.changed", "merchant.status.changed",
                "audit.recorded");
        assertThat(EventTypes.dataType(EventTypes.AUDIT_RECORDED)).isEqualTo(AuditRecordedData.class);
        assertThat(EventTypes.isKnown("parcel.deleted")).isFalse();
        assertThat(EventTypes.isKnown(null)).isFalse();
        assertThatThrownBy(() -> EventTypes.dataType("parcel.deleted")).isInstanceOf(IllegalArgumentException.class);
        assertThat(DeliveryUnassignedData.REASON_COURIER_DEACTIVATED).isEqualTo("COURIER_DEACTIVATED");
    }

    @Test
    @DisplayName("BR-28: the staff note never appears in the string form of parcel.status.changed")
    void noteNotInToString() {
        ParcelStatusChangedData data = new ParcelStatusChangedData(UUID.randomUUID(), "SD-7K3M9QX2TA", 4,
                ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED, Instant.now(), UUID.randomUUID(),
                ActorRole.COURIER, "Called twice, gate locked", FailureReason.OTHER, OwnerType.CUSTOMER,
                UUID.randomUUID(), null, null);
        assertThat(data.toString()).doesNotContain("Called twice").contains("FAILED");
    }

    @Test
    @DisplayName("SRS 3.5: an envelope without required parts is rejected")
    void envelopeRequiresParts() {
        assertThatThrownBy(() -> EventEnvelope.create(EventTypes.AUDIT_RECORDED, Instant.now(), UUID.randomUUID(),
                null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EventEnvelope<>(UUID.randomUUID(), "audit.recorded", 1, Instant.now(), null,
                Map.of())).isInstanceOf(NullPointerException.class);
    }

    private static void assertFields(Class<? extends Record> type, String... expected) {
        List<String> names = Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
        assertThat(names).as(type.getSimpleName()).containsExactly(expected);
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> out = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(out::add);
        return out;
    }
}

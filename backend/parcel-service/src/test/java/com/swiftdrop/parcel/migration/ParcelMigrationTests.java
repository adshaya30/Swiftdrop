package com.swiftdrop.parcel.migration;

import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelRepository;
import com.swiftdrop.parcel.support.AbstractParcelDatabaseTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.swiftdrop.parcel.support.ParcelFixtures.parcel;
import static org.assertj.core.api.Assertions.*;

class ParcelMigrationTests extends AbstractParcelDatabaseTest {
    @Autowired ParcelRepository parcels;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("BR-02/03 SRS 6.3: owner XOR, status, size and tracking uniqueness are enforced")
    void checksParcelConstraints() {
        Parcel saved = parcels.saveAndFlush(parcel("SD-0000000003", UUID.randomUUID(), null, null));
        try {
            assertThatThrownBy(() -> jdbc.update("UPDATE parcels SET customer_id=NULL WHERE id=?", saved.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE parcels SET merchant_id=? WHERE id=?", UUID.randomUUID(), saved.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE parcels SET status='UNKNOWN' WHERE id=?", saved.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE parcels SET size='HUGE' WHERE id=?", saved.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> parcels.saveAndFlush(parcel("SD-0000000003", UUID.randomUUID(), null, null)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            parcels.deleteById(saved.getId());
        }
    }

    @Test
    @DisplayName("BR-22: reference uniqueness includes DELIVERED but excludes CANCELLED and FAILED")
    void merchantReferencePartialIndex() {
        UUID merchant = UUID.randomUUID();
        Parcel first = parcels.saveAndFlush(parcel("SD-0000000004", null, merchant, "REF-1"));
        try {
            for (String status : new String[]{"CREATED", "DELIVERED"}) {
                jdbc.update("UPDATE parcels SET status=? WHERE id=?", status, first.getId());
                assertThatThrownBy(() -> parcels.saveAndFlush(parcel("SD-0000000005", null, merchant, "REF-1")))
                        .isInstanceOf(DataIntegrityViolationException.class);
            }
            for (String status : new String[]{"CANCELLED", "FAILED"}) {
                jdbc.update("UPDATE parcels SET status=? WHERE id=?", status, first.getId());
                Parcel next = parcels.saveAndFlush(parcel("SD-0000000005", null, merchant, "REF-1"));
                parcels.deleteById(next.getId());
            }
            Parcel other = parcels.saveAndFlush(parcel("SD-0000000006", null, UUID.randomUUID(), "REF-1"));
            parcels.deleteById(other.getId());
        } finally {
            parcels.deleteById(first.getId());
        }
    }

    @Test
    @DisplayName("FR-PAR-006 SRS 6.3: idempotency JSONB, owner-scoped key and shared outbox storage")
    void idempotencyAndOutboxStorage() {
        Parcel saved = parcels.saveAndFlush(parcel("SD-0000000007", UUID.randomUUID(), null, null));
        UUID keyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        try {
            insertKey(keyId, saved, saved.getCustomerId());
            assertThat(jdbc.queryForObject("SELECT response_body->>'trackingNumber' FROM idempotency_keys WHERE id=?", String.class, keyId))
                    .isEqualTo(saved.getTrackingNumber());
            assertThatThrownBy(() -> insertKey(UUID.randomUUID(), saved, saved.getCustomerId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            insertKey(UUID.randomUUID(), saved, UUID.randomUUID());
            jdbc.update("INSERT INTO outbox_events(id,event_type,routing_key,payload) VALUES (?,?,?,CAST(? AS JSONB))",
                    eventId, "parcel.created", "parcel.created", "{\"schemaVersion\":1}");
            assertThat(jdbc.queryForObject("SELECT attempts FROM outbox_events WHERE id=?", Integer.class, eventId)).isZero();
            assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename='parcels'", String.class))
                    .contains("idx_parcels_customer_created", "idx_parcels_merchant_created", "idx_parcels_status_created", "idx_parcels_terminal_at", "uq_parcels_merchant_reference");
            assertThat(jdbc.queryForObject("SELECT to_regclass('processed_events') IS NULL", Boolean.class)).isTrue();
        } finally {
            jdbc.update("DELETE FROM idempotency_keys WHERE parcel_id=?", saved.getId());
            jdbc.update("DELETE FROM outbox_events WHERE id=?", eventId);
            parcels.deleteById(saved.getId());
        }
    }

    private void insertKey(UUID id, Parcel parcel, UUID owner) {
        jdbc.update("INSERT INTO idempotency_keys(id,owner_type,owner_id,idempotency_key,request_hash,response_status,response_body,parcel_id) "
                        + "VALUES (?,'CUSTOMER',?,'test-key',?,201,CAST(? AS JSONB),?)", id, owner, "a".repeat(64),
                "{\"trackingNumber\":\"" + parcel.getTrackingNumber() + "\"}", parcel.getId());
    }
}

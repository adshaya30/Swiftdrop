package com.swiftdrop.parcel.create.repository;

import com.swiftdrop.common.enums.OwnerType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** FR-PAR-006: use the existing V1 table and owner/key uniqueness; never reserve in-progress rows. */
@Repository
public class IdempotencyRepository {
    private final JdbcTemplate jdbc;

    public IdempotencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<StoredResponse> findActive(OwnerType ownerType, UUID ownerId, String key, Instant cutoff) {
        return jdbc.query("""
                SELECT request_hash, response_status, response_body::text, parcel_id
                FROM idempotency_keys
                WHERE owner_type=? AND owner_id=? AND idempotency_key=? AND created_at>?
                """, (rs, row) -> new StoredResponse(rs.getString(1), rs.getInt(2),
                        rs.getString(3), rs.getObject(4, UUID.class)),
                ownerType.name(), ownerId, key, Timestamp.from(cutoff)).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insertSuccess(OwnerType ownerType, UUID ownerId, String key, String hash,
                              int status, String body, UUID parcelId, Instant now, Instant cutoff) {
        // Remove an expired owner/key row in the SAME transaction as its replacement.
        jdbc.update("""
                DELETE FROM idempotency_keys
                WHERE owner_type=? AND owner_id=? AND idempotency_key=? AND created_at<=?
                """, ownerType.name(), ownerId, key, Timestamp.from(cutoff));
        // Deliberately propagate PostgreSQL uniqueness failures. Caller must roll back before rereading.
        jdbc.update("""
                INSERT INTO idempotency_keys
                (id,owner_type,owner_id,idempotency_key,request_hash,response_status,response_body,parcel_id,created_at)
                VALUES (?,?,?,?,?,?,CAST(? AS JSONB),?,?)
                """, UUID.randomUUID(), ownerType.name(), ownerId, key, hash, status, body,
                parcelId, Timestamp.from(now));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int deleteExpired(Instant cutoff) {
        return jdbc.update("DELETE FROM idempotency_keys WHERE created_at<=?", Timestamp.from(cutoff));
    }

    public record StoredResponse(String requestHash, int status, String body, UUID parcelId) { }
}

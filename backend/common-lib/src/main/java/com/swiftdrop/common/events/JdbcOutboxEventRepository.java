package com.swiftdrop.common.events;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PostgreSQL implementation. Uses the service's DataSource, so inside a Spring transaction (JPA or JDBC) it shares the
 * caller's connection and commits or rolls back with the business change. Parameterised SQL only (NFR-SEC-006).
 */
public class JdbcOutboxEventRepository implements OutboxEventRepository {

    private static final String COLUMNS =
            "id, event_type, routing_key, payload, correlation_id, created_at, published_at, attempts";

    private final JdbcTemplate jdbc;

    public JdbcOutboxEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(OutboxEvent e) {
        jdbc.update("INSERT INTO outbox_events (" + COLUMNS + ") VALUES (?, ?, ?, CAST(? AS JSONB), ?, ?, ?, ?)",
                e.id(), e.eventType(), e.routingKey(), e.payload(), e.correlationId(),
                utc(e.createdAt()), utc(e.publishedAt()), e.attempts());
    }

    @Override
    public List<OutboxEvent> lockUnpublishedBatch(int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM outbox_events WHERE published_at IS NULL"
                + " ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED", JdbcOutboxEventRepository::map, limit);
    }

    @Override
    public void markPublished(UUID id, Instant publishedAt) {
        jdbc.update("UPDATE outbox_events SET published_at = ? WHERE id = ?", utc(publishedAt), id);
    }

    @Override
    public void incrementAttempts(UUID id) {
        jdbc.update("UPDATE outbox_events SET attempts = attempts + 1 WHERE id = ?", id);
    }

    @Override
    public int deletePublishedBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM outbox_events WHERE published_at IS NOT NULL AND published_at < ?",
                utc(cutoff));
    }

    @Override
    public long countUnpublished() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL", Long.class);
        return n == null ? 0 : n;
    }

    @Override
    public boolean tableExists() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT to_regclass('outbox_events') IS NOT NULL", Boolean.class));
    }

    private static OutboxEvent map(ResultSet rs, int row) throws SQLException {
        return new OutboxEvent(
                rs.getObject("id", UUID.class),
                rs.getString("event_type"),
                rs.getString("routing_key"),
                rs.getString("payload"),
                rs.getObject("correlation_id", UUID.class),
                instant(rs.getObject("created_at", OffsetDateTime.class)),
                instant(rs.getObject("published_at", OffsetDateTime.class)),
                rs.getInt("attempts"));
    }

    private static OffsetDateTime utc(Instant i) {
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime t) {
        return t == null ? null : t.toInstant();
    }
}

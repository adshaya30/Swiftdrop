package com.swiftdrop.common.events;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Test double of the outbox table (the PostgreSQL behaviour itself is covered by the PostgreSQL tests). */
class InMemoryOutboxEventRepository implements OutboxEventRepository {

    final List<OutboxEvent> rows = new ArrayList<>();
    boolean tableExists = true;
    RuntimeException failure;

    @Override
    public synchronized void insert(OutboxEvent event) {
        check();
        rows.add(event);
    }

    @Override
    public synchronized List<OutboxEvent> lockUnpublishedBatch(int limit) {
        check();
        return rows.stream().filter(r -> r.publishedAt() == null)
                .sorted(Comparator.comparing(OutboxEvent::createdAt))
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized void markPublished(UUID id, Instant publishedAt) {
        replace(id, r -> new OutboxEvent(r.id(), r.eventType(), r.routingKey(), r.payload(), r.correlationId(),
                r.createdAt(), publishedAt, r.attempts()));
    }

    @Override
    public synchronized void incrementAttempts(UUID id) {
        replace(id, r -> new OutboxEvent(r.id(), r.eventType(), r.routingKey(), r.payload(), r.correlationId(),
                r.createdAt(), r.publishedAt(), r.attempts() + 1));
    }

    @Override
    public synchronized int deletePublishedBefore(Instant cutoff) {
        int before = rows.size();
        rows.removeIf(r -> r.publishedAt() != null && r.publishedAt().isBefore(cutoff));
        return before - rows.size();
    }

    @Override
    public synchronized long countUnpublished() {
        return rows.stream().filter(r -> r.publishedAt() == null).count();
    }

    @Override
    public boolean tableExists() {
        check();
        return tableExists;
    }

    synchronized OutboxEvent get(UUID id) {
        return rows.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    private void check() {
        if (failure != null) {
            throw failure;
        }
    }

    private void replace(UUID id, java.util.function.UnaryOperator<OutboxEvent> change) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(id)) {
                rows.set(i, change.apply(rows.get(i)));
                return;
            }
        }
    }
}

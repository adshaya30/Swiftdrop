package com.swiftdrop.common.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Access to {@code outbox_events}. Every method joins the caller's transaction. */
public interface OutboxEventRepository {

    void insert(OutboxEvent event);

    /** Up to {@code limit} unpublished rows, oldest first, locked with FOR UPDATE SKIP LOCKED (SRS 3.5). */
    List<OutboxEvent> lockUnpublishedBatch(int limit);

    void markPublished(UUID id, Instant publishedAt);

    void incrementAttempts(UUID id);

    /** Deletes published rows older than the cut-off; returns the number deleted. */
    int deletePublishedBefore(Instant cutoff);

    long countUnpublished();

    /** False in services without an outbox (tracking, notification). */
    boolean tableExists();
}

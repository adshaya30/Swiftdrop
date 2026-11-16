package com.swiftdrop.notification.repository;

import com.swiftdrop.notification.entity.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * FR-NTF-004: find existing notification for idempotency check.
     */
    Optional<Notification> findByEventIdAndChannelAndRecipientKind(UUID eventId, String channel, String recipientKind);

    @Query("SELECT n FROM Notification n WHERE n.eventId = :eventId ORDER BY n.createdAt, n.channel, n.recipientKind")
    List<Notification> findAllByEventId(@Param("eventId") UUID eventId);

    /** Insert a planned row atomically so simultaneous redeliveries cannot race the unique key. */
    @Modifying
    @Query(value = """
            INSERT INTO notifications (event_id, event_type, tracking_number, channel, recipient_kind, status,
                template_code, attempts, created_at)
            VALUES (:eventId, :eventType, :trackingNumber, :channel, :recipientKind, 'PENDING', :templateCode, 0, now())
            ON CONFLICT (event_id, channel, recipient_kind) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId, @Param("eventType") String eventType,
                       @Param("trackingNumber") String trackingNumber, @Param("channel") String channel,
                       @Param("recipientKind") String recipientKind, @Param("templateCode") String templateCode);

    /**
     * (S) FR-NTF-008: recent notifications for the internal demo endpoint.
     */
    @Query("SELECT n FROM Notification n ORDER BY n.createdAt DESC")
    List<Notification> findRecent(@Param("limit") int limit);

    List<Notification> findTop50ByOrderByCreatedAtDesc();

    /**
     * Find pending notifications for the dispatcher (status = 'PENDING').
     * The caller may limit the number of rows processed in one batch.
     */
    @Query("SELECT n FROM Notification n WHERE n.status = 'PENDING' ORDER BY n.createdAt ASC")
    List<Notification> findPending();

    /**
     * (S) FR-WHK-010: retention cleanup of notifications older than 90 days.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") java.time.Instant cutoff);
}


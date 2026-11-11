package com.swiftdrop.notification.repository;

import com.swiftdrop.notification.entity.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * FR-NTF-004: find existing notification for idempotency check.
     */
    Optional<Notification> findByEventIdAndChannelAndRecipientKind(UUID eventId, String channel, String recipientKind);

    /**
     * (S) FR-NTF-008: recent notifications for the internal demo endpoint.
     */
    @Query("SELECT n FROM Notification n ORDER BY n.createdAt DESC")
    List<Notification> findRecent(@Param("limit") int limit);

    List<Notification> findTop50ByOrderByCreatedAtDesc();

    /**
     * (S) FR-WHK-010: retention cleanup of notifications older than 90 days.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") java.time.Instant cutoff);
}


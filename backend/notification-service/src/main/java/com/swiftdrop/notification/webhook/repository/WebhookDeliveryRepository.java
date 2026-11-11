package com.swiftdrop.notification.webhook.repository;

import com.swiftdrop.notification.webhook.entity.WebhookDelivery;
import com.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link WebhookDelivery} (SRS 6.3, FR-WHK-004).
 */
@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    Optional<WebhookDelivery> findByEventId(UUID eventId);

    boolean existsByEventId(UUID eventId);

    Optional<WebhookDelivery> findByIdAndMerchantId(UUID id, UUID merchantId);

    Page<WebhookDelivery> findByMerchantId(UUID merchantId, Pageable pageable);

    Page<WebhookDelivery> findByMerchantIdAndStatus(UUID merchantId, WebhookDeliveryStatus status, Pageable pageable);

    long countByMerchantIdAndStatus(UUID merchantId, WebhookDeliveryStatus status);

    @Query("SELECT COUNT(w) FROM WebhookDelivery w WHERE w.merchantId = :merchantId AND w.status = :status AND " +
            "((w.completedAt IS NOT NULL AND w.completedAt >= :since) OR (w.completedAt IS NULL AND w.lastAttemptAt IS NOT NULL AND w.lastAttemptAt >= :since))")
    long countCompletedSince(@Param("merchantId") UUID merchantId,
                             @Param("status") WebhookDeliveryStatus status,
                             @Param("since") Instant since);

    @Query(value = "SELECT * FROM webhook_deliveries WHERE status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= :now) ORDER BY next_attempt_at ASC NULLS FIRST LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<WebhookDelivery> claimDueDeliveries(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying
    @Query("DELETE FROM WebhookDelivery w WHERE w.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}

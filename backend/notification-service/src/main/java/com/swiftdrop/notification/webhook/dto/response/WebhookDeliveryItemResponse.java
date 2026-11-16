package com.swiftdrop.notification.webhook.dto.response;

import com.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Item in the webhook delivery log (FR-WHK-004).
 */
public record WebhookDeliveryItemResponse(
        UUID id,
        UUID eventId,
        String eventType,
        String trackingNumber,
        String targetUrl,
        WebhookDeliveryStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Integer lastHttpStatus,
        String lastError,
        Instant createdAt,
        Instant lastAttemptAt,
        Instant completedAt
) {}


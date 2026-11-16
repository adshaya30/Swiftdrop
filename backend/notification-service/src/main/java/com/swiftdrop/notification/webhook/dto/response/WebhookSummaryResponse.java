package com.swiftdrop.notification.webhook.dto.response;

/**
 * Summary counters for merchant webhook deliveries (FR-WHK-006).
 */
public record WebhookSummaryResponse(
        long failed24h,
        long pending,
        long succeeded24h
) {}


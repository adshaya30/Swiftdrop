package com.swiftdrop.notification.webhook.entity;

/**
 * Status of a webhook delivery attempt (SRS 6.2, FR-WHK-004).
 */
public enum WebhookDeliveryStatus {
    PENDING,
    SUCCEEDED,
    FAILED
}


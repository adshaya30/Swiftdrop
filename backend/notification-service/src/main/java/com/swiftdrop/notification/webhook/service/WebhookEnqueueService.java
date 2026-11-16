package com.swiftdrop.notification.webhook.service;

import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.data.ParcelStatusChangedData;
import com.swiftdrop.notification.webhook.entity.WebhookDelivery;
import java.util.UUID;

/**
 * Service for enqueueing webhook deliveries (SRS 8.1, FR-WHK-002).
 */
public interface WebhookEnqueueService {

    /**
     * Enqueues a webhook delivery for {@code parcel.status.changed} event.
     * No-op unless the event has a {@code merchantId} and that merchant's config exists and is enabled.
     * Idempotent on {@code eventId}.
     *
     * @param envelope the event envelope
     */
    void enqueueStatusChanged(EventEnvelope<ParcelStatusChangedData> envelope);

    /**
     * Enqueues a test ping delivery for a merchant.
     *
     * @param merchantId the merchant ID
     * @param targetUrl  the target URL
     * @return the created webhook delivery
     */
    WebhookDelivery enqueueTestPing(UUID merchantId, String targetUrl);
}


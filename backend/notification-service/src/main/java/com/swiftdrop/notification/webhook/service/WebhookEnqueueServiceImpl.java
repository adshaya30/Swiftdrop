package com.swiftdrop.notification.webhook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.data.ParcelStatusChangedData;
import com.swiftdrop.notification.webhook.entity.MerchantWebhookConfig;
import com.swiftdrop.notification.webhook.entity.WebhookDelivery;
import com.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.swiftdrop.notification.webhook.repository.MerchantWebhookConfigRepository;
import com.swiftdrop.notification.webhook.repository.WebhookDeliveryRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link WebhookEnqueueService} (FR-WHK-002).
 */
@Service
public class WebhookEnqueueServiceImpl implements WebhookEnqueueService {

    private static final Logger log = LoggerFactory.getLogger(WebhookEnqueueServiceImpl.class);
    private static final String PARCEL_STATUS_CHANGED_TYPE = "parcel.status.changed";
    private static final String WEBHOOK_TEST_TYPE = "webhook.test";

    private final MerchantWebhookConfigRepository configRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;

    public WebhookEnqueueServiceImpl(MerchantWebhookConfigRepository configRepository,
                                    WebhookDeliveryRepository deliveryRepository,
                                    ObjectMapper objectMapper) {
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
        this.deliveryRepository = Objects.requireNonNull(deliveryRepository, "deliveryRepository");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    @Transactional
    public void enqueueStatusChanged(EventEnvelope<ParcelStatusChangedData> envelope) {
        if (envelope == null || envelope.data() == null) {
            return;
        }

        ParcelStatusChangedData data = envelope.data();
        UUID merchantId = data.merchantId();
        if (merchantId == null) {
            log.debug("Event {} has no merchantId; skipping webhook enqueue", envelope.eventId());
            return;
        }

        MerchantWebhookConfig config = configRepository.findById(merchantId).orElse(null);
        if (config == null || !config.isEnabled()) {
            log.debug("Merchant {} has no active webhook config; skipping webhook enqueue", merchantId);
            return;
        }

        if (deliveryRepository.existsByEventId(envelope.eventId())) {
            log.debug("Webhook delivery for event {} already exists; skipping duplicate", envelope.eventId());
            return;
        }

        String payload = buildStatusChangedPayload(envelope.eventId(), envelope.occurredAt(), data);
        WebhookDelivery delivery = new WebhookDelivery(
                envelope.eventId(),
                merchantId,
                PARCEL_STATUS_CHANGED_TYPE,
                data.trackingNumber(),
                payload,
                config.getUrl(),
                WebhookDeliveryStatus.PENDING,
                Instant.now()
        );

        try {
            deliveryRepository.save(delivery);
            log.info("Enqueued webhook delivery for event {} merchant {}", envelope.eventId(), merchantId);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Webhook delivery for event {} was enqueued concurrently", envelope.eventId());
        }
    }

    @Override
    @Transactional
    public WebhookDelivery enqueueTestPing(UUID merchantId, String targetUrl) {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(targetUrl, "targetUrl");

        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        String payload = buildTestPingPayload(eventId, now);

        WebhookDelivery delivery = new WebhookDelivery(
                eventId,
                merchantId,
                WEBHOOK_TEST_TYPE,
                null,
                payload,
                targetUrl,
                WebhookDeliveryStatus.PENDING,
                now
        );

        return deliveryRepository.save(delivery);
    }

    private String buildStatusChangedPayload(UUID eventId, Instant occurredAt, ParcelStatusChangedData data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", eventId.toString());
        payload.put("type", PARCEL_STATUS_CHANGED_TYPE);
        payload.put("createdAt", occurredAt.toString());

        Map<String, Object> innerData = new LinkedHashMap<>();
        innerData.put("parcelId", data.parcelId() != null ? data.parcelId().toString() : null);
        innerData.put("trackingNumber", data.trackingNumber());
        innerData.put("merchantReference", data.merchantReference());
        innerData.put("sequence", data.sequence());
        innerData.put("previousStatus", data.previousStatus() != null ? data.previousStatus().name() : null);
        innerData.put("status", data.status() != null ? data.status().name() : null);
        innerData.put("statusChangedAt", data.statusChangedAt() != null ? data.statusChangedAt().toString() : null);
        innerData.put("failureReasonCode", data.failureReasonCode() != null ? data.failureReasonCode().name() : null);

        payload.put("data", innerData);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize webhook payload", e);
        }
    }

    private String buildTestPingPayload(UUID eventId, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", eventId.toString());
        payload.put("type", WEBHOOK_TEST_TYPE);
        payload.put("createdAt", occurredAt.toString());

        Map<String, Object> innerData = new LinkedHashMap<>();
        innerData.put("message", "SwiftDrop webhook test");

        payload.put("data", innerData);

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize test ping webhook payload", e);
        }
    }
}


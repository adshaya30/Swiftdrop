package com.swiftdrop.notification.webhook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.enums.FailureReason;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.data.ParcelStatusChangedData;
import com.swiftdrop.notification.webhook.entity.MerchantWebhookConfig;
import com.swiftdrop.notification.webhook.entity.WebhookDelivery;
import com.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.swiftdrop.notification.webhook.repository.MerchantWebhookConfigRepository;
import com.swiftdrop.notification.webhook.repository.WebhookDeliveryRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("FR-WHK-002")
class WebhookEnqueueServiceTest {

    @Mock
    private MerchantWebhookConfigRepository configRepository;

    @Mock
    private WebhookDeliveryRepository deliveryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebhookEnqueueServiceImpl enqueueService;

    @BeforeEach
    void setUp() {
        enqueueService = new WebhookEnqueueServiceImpl(configRepository, deliveryRepository, objectMapper);
    }

    @Test
    @DisplayName("FR-WHK-002: Enqueue is a no-op when merchantId is null")
    void enqueueStatusChanged_noMerchantId_noOp() {
        UUID eventId = UUID.randomUUID();
        ParcelStatusChangedData data = new ParcelStatusChangedData(
                UUID.randomUUID(), "SD-1234567890", 1, null, ParcelStatus.CREATED,
                Instant.now(), UUID.randomUUID(), null, "secret staff note", null, null, null, null, null
        );
        EventEnvelope<ParcelStatusChangedData> envelope = new EventEnvelope<>(
                eventId, "parcel.status.changed", 1, Instant.now(), UUID.randomUUID(), data
        );

        enqueueService.enqueueStatusChanged(envelope);

        verify(configRepository, never()).findById(any());
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    @DisplayName("FR-WHK-002: Enqueue is a no-op when merchant config does not exist")
    void enqueueStatusChanged_configNotFound_noOp() {
        UUID merchantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ParcelStatusChangedData data = new ParcelStatusChangedData(
                UUID.randomUUID(), "SD-1234567890", 1, null, ParcelStatus.CREATED,
                Instant.now(), UUID.randomUUID(), null, "note", null, null, null, merchantId, "ORDER-1"
        );
        EventEnvelope<ParcelStatusChangedData> envelope = new EventEnvelope<>(
                eventId, "parcel.status.changed", 1, Instant.now(), UUID.randomUUID(), data
        );

        when(configRepository.findById(merchantId)).thenReturn(Optional.empty());

        enqueueService.enqueueStatusChanged(envelope);

        verify(deliveryRepository, never()).save(any());
    }

    @Test
    @DisplayName("FR-WHK-002: Enqueue is a no-op when merchant config is disabled")
    void enqueueStatusChanged_configDisabled_noOp() {
        UUID merchantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ParcelStatusChangedData data = new ParcelStatusChangedData(
                UUID.randomUUID(), "SD-1234567890", 1, null, ParcelStatus.CREATED,
                Instant.now(), UUID.randomUUID(), null, "note", null, null, null, merchantId, "ORDER-1"
        );
        EventEnvelope<ParcelStatusChangedData> envelope = new EventEnvelope<>(
                eventId, "parcel.status.changed", 1, Instant.now(), UUID.randomUUID(), data
        );

        MerchantWebhookConfig config = new MerchantWebhookConfig(merchantId, "https://merchant.example/webhook", new byte[32], false);
        when(configRepository.findById(merchantId)).thenReturn(Optional.of(config));

        enqueueService.enqueueStatusChanged(envelope);

        verify(deliveryRepository, never()).save(any());
    }

    @Test
    @DisplayName("FR-WHK-002: Enqueue is idempotent when eventId already exists")
    void enqueueStatusChanged_alreadyExists_idempotent() {
        UUID merchantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ParcelStatusChangedData data = new ParcelStatusChangedData(
                UUID.randomUUID(), "SD-1234567890", 1, null, ParcelStatus.CREATED,
                Instant.now(), UUID.randomUUID(), null, "note", null, null, null, merchantId, "ORDER-1"
        );
        EventEnvelope<ParcelStatusChangedData> envelope = new EventEnvelope<>(
                eventId, "parcel.status.changed", 1, Instant.now(), UUID.randomUUID(), data
        );

        MerchantWebhookConfig config = new MerchantWebhookConfig(merchantId, "https://merchant.example/webhook", new byte[32], true);
        when(configRepository.findById(merchantId)).thenReturn(Optional.of(config));
        when(deliveryRepository.existsByEventId(eventId)).thenReturn(true);

        enqueueService.enqueueStatusChanged(envelope);

        verify(deliveryRepository, never()).save(any());
    }

    @Test
    @DisplayName("FR-WHK-002: Enqueues delivery with exact payload, no personal data, no staff note")
    void enqueueStatusChanged_success() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        Instant statusChangedAt = Instant.parse("2026-11-10T10:15:30Z");
        Instant occurredAt = Instant.parse("2026-11-10T10:15:31Z");

        ParcelStatusChangedData data = new ParcelStatusChangedData(
                parcelId, "SD-7K3M9QX2TA", 2, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT,
                statusChangedAt, UUID.randomUUID(), null, "Internal staff note: fragile parcel",
                FailureReason.RECIPIENT_UNAVAILABLE, null, null, merchantId, "REF-9988"
        );
        EventEnvelope<ParcelStatusChangedData> envelope = new EventEnvelope<>(
                eventId, "parcel.status.changed", 1, occurredAt, UUID.randomUUID(), data
        );

        MerchantWebhookConfig config = new MerchantWebhookConfig(merchantId, "https://merchant.example/webhook", new byte[32], true);
        when(configRepository.findById(merchantId)).thenReturn(Optional.of(config));
        when(deliveryRepository.existsByEventId(eventId)).thenReturn(false);

        enqueueService.enqueueStatusChanged(envelope);

        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryRepository).save(captor.capture());

        WebhookDelivery saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getMerchantId()).isEqualTo(merchantId);
        assertThat(saved.getEventType()).isEqualTo("parcel.status.changed");
        assertThat(saved.getTrackingNumber()).isEqualTo("SD-7K3M9QX2TA");
        assertThat(saved.getTargetUrl()).isEqualTo("https://merchant.example/webhook");
        assertThat(saved.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(saved.getAttemptCount()).isEqualTo(0);
        assertThat(saved.getNextAttemptAt()).isNotNull();

        // Validate payload content and ensure NO staff note or personal data is present
        JsonNode payload = objectMapper.readTree(saved.getPayload());
        assertThat(payload.get("id").asText()).isEqualTo(eventId.toString());
        assertThat(payload.get("type").asText()).isEqualTo("parcel.status.changed");
        assertThat(payload.get("createdAt").asText()).isEqualTo("2026-11-10T10:15:31Z");

        JsonNode innerData = payload.get("data");
        assertThat(innerData.get("parcelId").asText()).isEqualTo(parcelId.toString());
        assertThat(innerData.get("trackingNumber").asText()).isEqualTo("SD-7K3M9QX2TA");
        assertThat(innerData.get("merchantReference").asText()).isEqualTo("REF-9988");
        assertThat(innerData.get("sequence").asInt()).isEqualTo(2);
        assertThat(innerData.get("previousStatus").asText()).isEqualTo("PICKED_UP");
        assertThat(innerData.get("status").asText()).isEqualTo("IN_TRANSIT");
        assertThat(innerData.get("statusChangedAt").asText()).isEqualTo("2026-11-10T10:15:30Z");
        assertThat(innerData.get("failureReasonCode").asText()).isEqualTo("RECIPIENT_UNAVAILABLE");

        // Verify BR-28 / FR-WHK-002: no staff note, no personal data
        assertThat(innerData.has("note")).isFalse();
        assertThat(saved.getPayload()).doesNotContain("Internal staff note");
        assertThat(innerData.has("customer")).isFalse();
        assertThat(innerData.has("recipient")).isFalse();
    }

    @Test
    @DisplayName("FR-WHK-009: Test ping delivery has type webhook.test and test message")
    void enqueueTestPing_createsTestDelivery() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String url = "https://merchant.example/webhook";

        when(deliveryRepository.save(any(WebhookDelivery.class))).thenAnswer(inv -> inv.getArgument(0));

        WebhookDelivery delivery = enqueueService.enqueueTestPing(merchantId, url);

        assertThat(delivery.getMerchantId()).isEqualTo(merchantId);
        assertThat(delivery.getTargetUrl()).isEqualTo(url);
        assertThat(delivery.getEventType()).isEqualTo("webhook.test");
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getAttemptCount()).isEqualTo(0);

        JsonNode payload = objectMapper.readTree(delivery.getPayload());
        assertThat(payload.get("type").asText()).isEqualTo("webhook.test");
        assertThat(payload.get("data").get("message").asText()).isEqualTo("SwiftDrop webhook test");
    }
}


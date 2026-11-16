package com.swiftdrop.notification.webhook.service;

import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.paging.Pagination;
import com.swiftdrop.notification.webhook.dto.request.MerchantWebhookConfigRequest;
import com.swiftdrop.notification.webhook.dto.response.MerchantWebhookConfigResponse;
import com.swiftdrop.notification.webhook.dto.response.RotateSecretResponse;
import com.swiftdrop.notification.webhook.dto.response.WebhookDeliveryItemResponse;
import com.swiftdrop.notification.webhook.dto.response.WebhookSummaryResponse;
import com.swiftdrop.notification.webhook.entity.MerchantWebhookConfig;
import com.swiftdrop.notification.webhook.entity.WebhookDelivery;
import com.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.swiftdrop.notification.webhook.mapper.WebhookMapper;
import com.swiftdrop.notification.webhook.repository.MerchantWebhookConfigRepository;
import com.swiftdrop.notification.webhook.repository.WebhookDeliveryRepository;
import com.swiftdrop.notification.webhook.security.HmacSigner;
import com.swiftdrop.notification.webhook.security.SecretCipher;
import com.swiftdrop.notification.webhook.security.SsrfValidator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("FR-WHK-001")
@Tag("FR-WHK-003")
@Tag("FR-WHK-004")
@Tag("FR-WHK-006")
class MerchantWebhookServiceTest {

    @Mock
    private MerchantWebhookConfigRepository configRepository;

    @Mock
    private WebhookDeliveryRepository deliveryRepository;

    @Mock
    private WebhookEnqueueService enqueueService;

    @Mock
    private SsrfValidator ssrfValidator;

    @Mock
    private SecretCipher secretCipher;

    @Mock
    private HmacSigner hmacSigner;

    private final WebhookMapper webhookMapper = new WebhookMapper();
    private MerchantWebhookServiceImpl webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new MerchantWebhookServiceImpl(
                configRepository, deliveryRepository, enqueueService,
                ssrfValidator, secretCipher, hmacSigner, webhookMapper
        );
    }

    @Test
    @DisplayName("FR-WHK-001: First save generates secret and returns it once")
    void saveConfig_firstSave_generatesAndReturnsSecret() {
        UUID merchantId = UUID.randomUUID();
        MerchantWebhookConfigRequest request = new MerchantWebhookConfigRequest("https://example.com/webhook", true);
        String generatedSecret = "whsec_ABC1234567890abcdefghijklmnopqrstuvwxyz";
        byte[] encryptedSecret = new byte[]{1, 2, 3};

        when(configRepository.findById(merchantId)).thenReturn(Optional.empty());
        when(hmacSigner.generateSecret()).thenReturn(generatedSecret);
        when(secretCipher.encrypt(generatedSecret)).thenReturn(encryptedSecret);

        MerchantWebhookConfigResponse response = webhookService.saveConfig(merchantId, request);

        assertThat(response.url()).isEqualTo("https://example.com/webhook");
        assertThat(response.enabled()).isTrue();
        assertThat(response.hasSecret()).isTrue();
        assertThat(response.secret()).isEqualTo(generatedSecret);

        verify(ssrfValidator).validateUrl("https://example.com/webhook");
        verify(configRepository).save(any(MerchantWebhookConfig.class));
    }

    @Test
    @DisplayName("FR-WHK-001: Subsequent save updates config and never returns the secret")
    void saveConfig_subsequentSave_doesNotReturnSecret() {
        UUID merchantId = UUID.randomUUID();
        MerchantWebhookConfigRequest request = new MerchantWebhookConfigRequest("https://example.com/new-webhook", false);
        MerchantWebhookConfig existing = new MerchantWebhookConfig(merchantId, "https://example.com/old", new byte[]{1, 2}, true);

        when(configRepository.findById(merchantId)).thenReturn(Optional.of(existing));

        MerchantWebhookConfigResponse response = webhookService.saveConfig(merchantId, request);

        assertThat(response.url()).isEqualTo("https://example.com/new-webhook");
        assertThat(response.enabled()).isFalse();
        assertThat(response.hasSecret()).isTrue();
        assertThat(response.secret()).isNull();

        assertThat(existing.getUrl()).isEqualTo("https://example.com/new-webhook");
        assertThat(existing.isEnabled()).isFalse();
        verify(configRepository).save(existing);
    }

    @Test
    @DisplayName("FR-WHK-003: Rotate secret generates new secret and updates encrypted secret in database")
    void rotateSecret_success() {
        UUID merchantId = UUID.randomUUID();
        MerchantWebhookConfig existing = new MerchantWebhookConfig(merchantId, "https://example.com", new byte[]{1, 2}, true);
        String newSecret = "whsec_NEW_SECRET_1234567890abcdefghijklmnopqrstu";
        byte[] newEncrypted = new byte[]{9, 8, 7};

        when(configRepository.findById(merchantId)).thenReturn(Optional.of(existing));
        when(hmacSigner.generateSecret()).thenReturn(newSecret);
        when(secretCipher.encrypt(newSecret)).thenReturn(newEncrypted);

        RotateSecretResponse response = webhookService.rotateSecret(merchantId);

        assertThat(response.secret()).isEqualTo(newSecret);
        assertThat(existing.getSecretEncrypted()).isEqualTo(newEncrypted);
        verify(configRepository).save(existing);
    }

    @Test
    @DisplayName("FR-WHK-003: Rotate secret without saved config throws VALIDATION_ERROR")
    void rotateSecret_unconfigured_throwsValidationError() {
        UUID merchantId = UUID.randomUUID();
        when(configRepository.findById(merchantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> webhookService.rotateSecret(merchantId))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("FR-WHK-009: Test webhook enqueues webhook.test delivery")
    void triggerTest_success() {
        UUID merchantId = UUID.randomUUID();
        MerchantWebhookConfig config = new MerchantWebhookConfig(merchantId, "https://example.com/hook", new byte[]{1}, true);
        WebhookDelivery testDelivery = new WebhookDelivery(
                UUID.randomUUID(), merchantId, "webhook.test", null, "{}", "https://example.com/hook",
                WebhookDeliveryStatus.PENDING, Instant.now()
        );

        when(configRepository.findById(merchantId)).thenReturn(Optional.of(config));
        when(enqueueService.enqueueTestPing(merchantId, "https://example.com/hook")).thenReturn(testDelivery);

        WebhookDeliveryItemResponse response = webhookService.triggerTest(merchantId);
        assertThat(response.eventType()).isEqualTo("webhook.test");
        assertThat(response.targetUrl()).isEqualTo("https://example.com/hook");
    }

    @Test
    @DisplayName("FR-WHK-004: Retry delivery re-queues a FAILED delivery with attempt reset to 0")
    void retryDelivery_fromFailed_succeeds() {
        UUID merchantId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        WebhookDelivery delivery = new WebhookDelivery(
                UUID.randomUUID(), merchantId, "parcel.status.changed", "SD-1234567890",
                "{}", "https://example.com", WebhookDeliveryStatus.FAILED, null
        );
        delivery.setAttemptCount(6);
        delivery.setLastError("HTTP 500");

        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
        when(deliveryRepository.save(any(WebhookDelivery.class))).thenAnswer(inv -> inv.getArgument(0));

        WebhookDeliveryItemResponse response = webhookService.retryDelivery(merchantId, deliveryId);

        assertThat(response.status()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(response.attemptCount()).isEqualTo(0);
        assertThat(response.lastError()).isNull();
        assertThat(delivery.getNextAttemptAt()).isNotNull();
    }

    @Test
    @DisplayName("FR-WHK-004: Retry delivery throws INVALID_STATUS_TRANSITION when status is not FAILED")
    void retryDelivery_nonFailed_throwsConflict() {
        UUID merchantId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        WebhookDelivery delivery = new WebhookDelivery(
                UUID.randomUUID(), merchantId, "parcel.status.changed", "SD-1234567890",
                "{}", "https://example.com", WebhookDeliveryStatus.PENDING, null
        );

        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));

        assertThatThrownBy(() -> webhookService.retryDelivery(merchantId, deliveryId))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }

    @Test
    @DisplayName("FR-WHK-004: Retry delivery returns 404 NOT_FOUND for another merchant's delivery")
    void retryDelivery_otherMerchant_throwsNotFound() {
        UUID callerMerchantId = UUID.randomUUID();
        UUID otherMerchantId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        WebhookDelivery delivery = new WebhookDelivery(
                UUID.randomUUID(), otherMerchantId, "parcel.status.changed", "SD-1234567890",
                "{}", "https://example.com", WebhookDeliveryStatus.FAILED, null
        );

        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));

        assertThatThrownBy(() -> webhookService.retryDelivery(callerMerchantId, deliveryId))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("FR-WHK-006: Get summary returns counts for caller's merchant")
    void getSummary_returnsCounts() {
        UUID merchantId = UUID.randomUUID();
        when(deliveryRepository.countByMerchantIdAndStatus(merchantId, WebhookDeliveryStatus.PENDING)).thenReturn(2L);
        when(deliveryRepository.countCompletedSince(eq(merchantId), eq(WebhookDeliveryStatus.SUCCEEDED), any(Instant.class))).thenReturn(15L);
        when(deliveryRepository.countCompletedSince(eq(merchantId), eq(WebhookDeliveryStatus.FAILED), any(Instant.class))).thenReturn(1L);

        WebhookSummaryResponse summary = webhookService.getSummary(merchantId);

        assertThat(summary.pending()).isEqualTo(2L);
        assertThat(summary.succeeded24h()).isEqualTo(15L);
        assertThat(summary.failed24h()).isEqualTo(1L);
    }
}


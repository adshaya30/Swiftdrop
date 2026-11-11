package com.swiftdrop.notification.webhook.service;

import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.notification.webhook.config.RetrySchedule;
import com.swiftdrop.notification.webhook.entity.MerchantWebhookConfig;
import com.swiftdrop.notification.webhook.entity.WebhookDelivery;
import com.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.swiftdrop.notification.webhook.repository.MerchantWebhookConfigRepository;
import com.swiftdrop.notification.webhook.repository.WebhookDeliveryRepository;
import com.swiftdrop.notification.webhook.security.HmacSigner;
import com.swiftdrop.notification.webhook.security.SecretCipher;
import com.swiftdrop.notification.webhook.security.SsrfValidator;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("TST-024")
@Tag("FR-WHK-003")
@Tag("FR-WHK-004")
@Tag("FR-WHK-005")
class WebhookSenderServiceTest {

    @Mock
    private MerchantWebhookConfigRepository configRepository;

    @Mock
    private WebhookDeliveryRepository deliveryRepository;

    @Mock
    private SsrfValidator ssrfValidator;

    @Mock
    private SecretCipher secretCipher;

    @Mock
    private HmacSigner hmacSigner;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<Void> httpResponse;

    private RetrySchedule retrySchedule;
    private WebhookSenderService senderService;

    @BeforeEach
    void setUp() {
        retrySchedule = new RetrySchedule("PT1M,PT5M,PT30M,PT2H,PT6H", 6);
        senderService = new WebhookSenderService(
                configRepository, deliveryRepository, ssrfValidator,
                secretCipher, hmacSigner, retrySchedule, httpClient, 10
        );
        lenient().when(hmacSigner.sign(any(Long.class), any(String.class), any(String.class)))
                .thenReturn("sha256=abcdef123456");
    }

    @Test
    @DisplayName("TST-024: Successful HTTP 200 response marks delivery SUCCEEDED with completed_at")
    void dispatchDelivery_http200_succeeds() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        WebhookDelivery delivery = new WebhookDelivery(
                eventId, merchantId, "parcel.status.changed", "SD-1234567890",
                "{\"id\":\"test\"}", "https://merchant.example/webhook", WebhookDeliveryStatus.PENDING, Instant.now()
        );

        MerchantWebhookConfig config = new MerchantWebhookConfig(merchantId, "https://merchant.example/webhook", new byte[]{1, 2}, true);
        when(configRepository.findById(merchantId)).thenReturn(Optional.of(config));
        when(secretCipher.decrypt(config.getSecretEncrypted())).thenReturn("whsec_testSecret12345");
        when(hmacSigner.sign(any(Long.class), any(String.class), any(String.class))).thenReturn("sha256=abcdef123456");
        when(httpResponse.statusCode()).thenReturn(200);
        doReturn(httpResponse).when(httpClient).send(any(), any());

        senderService.dispatchDelivery(delivery);

        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.SUCCEEDED);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getLastHttpStatus()).isEqualTo(200);
        assertThat(delivery.getLastError()).isNull();
        assertThat(delivery.getCompletedAt()).isNotNull();
        assertThat(delivery.getNextAttemptAt()).isNull();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());
        HttpRequest sentRequest = requestCaptor.getValue();
        assertThat(sentRequest.headers().firstValue("User-Agent")).contains("SwiftDrop-Webhook/1.0");
        assertThat(sentRequest.headers().firstValue("X-SwiftDrop-Event")).contains("parcel.status.changed");
        assertThat(sentRequest.headers().firstValue("X-SwiftDrop-Delivery")).contains(eventId.toString());
        assertThat(sentRequest.headers().firstValue("X-SwiftDrop-Attempt")).contains("1");
        assertThat(sentRequest.headers().firstValue("X-SwiftDrop-Signature")).contains("sha256=abcdef123456");

        verify(deliveryRepository).save(delivery);
    }

    @Test
    @DisplayName("TST-024: HTTP 500 error increments attempt count and schedules next attempt")
    void dispatchDelivery_http500_retries() throws Exception {
        UUID merchantId = UUID.randomUUID();
        WebhookDelivery delivery = new WebhookDelivery(
                UUID.randomUUID(), merchantId, "parcel.status.changed", "SD-1234567890",
                "{\"id\":\"test\"}", "https://merchant.example/webhook", WebhookDeliveryStatus.PENDING, Instant.now()
        );

        MerchantWebhookConfig config = new MerchantWebhookConfig(merchantId, "https://merchant.example/webhook", new byte[]{1, 2}, true);
        when(configRepository.findById(merchantId)).thenReturn(Optional.of(config));
        when(secretCipher.decrypt(config.getSecretEncrypted())).thenReturn("whsec_secret");
        when(httpResponse.statusCode()).thenReturn(500);
        doReturn(httpResponse).when(httpClient).send(any(), any());

        senderService.dispatchDelivery(delivery);

        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getLastHttpStatus()).isEqualTo(500);
        assertThat(delivery.getLastError()).isEqualTo("HTTP 500");
        assertThat(delivery.getNextAttemptAt()).isNotNull();

        verify(deliveryRepository).save(delivery);
    }

    @Test
    @DisplayName("TST-024: Sixth consecutive failure permanently transitions delivery to FAILED")
    void dispatchDelivery_sixthFailure_permanentlyFailed() throws Exception {
        UUID merchantId = UUID.randomUUID();
        WebhookDelivery delivery = new WebhookDelivery(
                UUID.randomUUID(), merchantId, "parcel.status.changed", "SD-1234567890",
                "{\"id\":\"test\"}", "https://merchant.example/webhook", WebhookDeliveryStatus.PENDING, Instant.now()
        );
        delivery.setAttemptCount(5); // 5 prior attempts

        MerchantWebhookConfig config = new MerchantWebhookConfig(merchantId, "https://merchant.example/webhook", new byte[]{1, 2}, true);
        when(configRepository.findById(merchantId)).thenReturn(Optional.of(config));
        when(secretCipher.decrypt(config.getSecretEncrypted())).thenReturn("whsec_secret");
        when(httpResponse.statusCode()).thenReturn(503);
        doReturn(httpResponse).when(httpClient).send(any(), any());

        senderService.dispatchDelivery(delivery);

        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getAttemptCount()).isEqualTo(6);
        assertThat(delivery.getLastHttpStatus()).isEqualTo(503);
        assertThat(delivery.getCompletedAt()).isNotNull();
        assertThat(delivery.getNextAttemptAt()).isNull();

        verify(deliveryRepository).save(delivery);
    }

    @Test
    @DisplayName("TST-024: Timeout during HTTP send records timeout error summary")
    void dispatchDelivery_timeout_recordsError() throws Exception {
        UUID merchantId = UUID.randomUUID();
        WebhookDelivery delivery = new WebhookDelivery(
                UUID.randomUUID(), merchantId, "parcel.status.changed", "SD-1234567890",
                "{\"id\":\"test\"}", "https://merchant.example/webhook", WebhookDeliveryStatus.PENDING, Instant.now()
        );

        MerchantWebhookConfig config = new MerchantWebhookConfig(merchantId, "https://merchant.example/webhook", new byte[]{1, 2}, true);
        when(configRepository.findById(merchantId)).thenReturn(Optional.of(config));
        when(secretCipher.decrypt(config.getSecretEncrypted())).thenReturn("whsec_secret");
        when(httpClient.send(any(HttpRequest.class), any())).thenThrow(new HttpTimeoutException("Request timed out"));

        senderService.dispatchDelivery(delivery);

        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getLastError()).contains("Timeout after 10s");
        verify(deliveryRepository).save(delivery);
    }

    @Test
    @DisplayName("FR-WHK-007: SSRF violation at send time records failed attempt")
    void dispatchDelivery_ssrfViolation_recordsFailure() {
        UUID merchantId = UUID.randomUUID();
        WebhookDelivery delivery = new WebhookDelivery(
                UUID.randomUUID(), merchantId, "parcel.status.changed", "SD-1234567890",
                "{\"id\":\"test\"}", "http://169.254.169.254/webhook", WebhookDeliveryStatus.PENDING, Instant.now()
        );

        MerchantWebhookConfig config = new MerchantWebhookConfig(merchantId, "http://169.254.169.254/webhook", new byte[]{1, 2}, true);
        when(configRepository.findById(merchantId)).thenReturn(Optional.of(config));
        doThrow(new DomainException(ErrorCode.VALIDATION_ERROR, "Restricted address")).when(ssrfValidator).validateUrl(any());

        senderService.dispatchDelivery(delivery);

        assertThat(delivery.getLastError()).contains("SSRF validation failed");
        verify(deliveryRepository).save(delivery);
    }

    @Test
    @DisplayName("FR-WHK-004: Missing or disabled config at send time counts as failed attempt")
    void dispatchDelivery_disabledConfig_recordsFailure() {
        UUID merchantId = UUID.randomUUID();
        WebhookDelivery delivery = new WebhookDelivery(
                UUID.randomUUID(), merchantId, "parcel.status.changed", "SD-1234567890",
                "{\"id\":\"test\"}", "https://merchant.example/webhook", WebhookDeliveryStatus.PENDING, Instant.now()
        );

        MerchantWebhookConfig config = new MerchantWebhookConfig(merchantId, "https://merchant.example/webhook", new byte[]{1, 2}, false);
        when(configRepository.findById(merchantId)).thenReturn(Optional.of(config));

        senderService.dispatchDelivery(delivery);

        assertThat(delivery.getLastError()).contains("missing or disabled");
        verify(deliveryRepository).save(delivery);
    }
}

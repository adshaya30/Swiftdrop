package com.swiftdrop.notification.webhook.service;

import com.swiftdrop.notification.webhook.config.RetrySchedule;
import com.swiftdrop.notification.webhook.entity.MerchantWebhookConfig;
import com.swiftdrop.notification.webhook.entity.WebhookDelivery;
import com.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.swiftdrop.notification.webhook.repository.MerchantWebhookConfigRepository;
import com.swiftdrop.notification.webhook.repository.WebhookDeliveryRepository;
import com.swiftdrop.notification.webhook.security.HmacSigner;
import com.swiftdrop.notification.webhook.security.SecretCipher;
import com.swiftdrop.notification.webhook.security.SsrfValidator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatches webhook HTTP requests per attempt (FR-WHK-003, FR-WHK-004, FR-WHK-005).
 * Sends outside the claim transaction using java.net.http.HttpClient.
 */
@Service
public class WebhookSenderService {

    private static final Logger log = LoggerFactory.getLogger(WebhookSenderService.class);
    private static final String USER_AGENT = "SwiftDrop-Webhook/1.0";
    private static final int MAX_ERROR_LENGTH = 200;

    private final MerchantWebhookConfigRepository configRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final SsrfValidator ssrfValidator;
    private final SecretCipher secretCipher;
    private final HmacSigner hmacSigner;
    private final RetrySchedule retrySchedule;
    private final HttpClient httpClient;
    private final int timeoutSeconds;

    @Autowired
    public WebhookSenderService(
            MerchantWebhookConfigRepository configRepository,
            WebhookDeliveryRepository deliveryRepository,
            SsrfValidator ssrfValidator,
            SecretCipher secretCipher,
            HmacSigner hmacSigner,
            RetrySchedule retrySchedule,
            @Value("${notification.webhook.timeout-seconds:10}") int timeoutSeconds) {
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
        this.deliveryRepository = Objects.requireNonNull(deliveryRepository, "deliveryRepository");
        this.ssrfValidator = Objects.requireNonNull(ssrfValidator, "ssrfValidator");
        this.secretCipher = Objects.requireNonNull(secretCipher, "secretCipher");
        this.hmacSigner = Objects.requireNonNull(hmacSigner, "hmacSigner");
        this.retrySchedule = Objects.requireNonNull(retrySchedule, "retrySchedule");
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 10;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(this.timeoutSeconds))
                .build();
    }

    /**
     * Package-private constructor allowing injection of a custom HttpClient for testing.
     */
    WebhookSenderService(
            MerchantWebhookConfigRepository configRepository,
            WebhookDeliveryRepository deliveryRepository,
            SsrfValidator ssrfValidator,
            SecretCipher secretCipher,
            HmacSigner hmacSigner,
            RetrySchedule retrySchedule,
            HttpClient httpClient,
            int timeoutSeconds) {
        this.configRepository = configRepository;
        this.deliveryRepository = deliveryRepository;
        this.ssrfValidator = ssrfValidator;
        this.secretCipher = secretCipher;
        this.hmacSigner = hmacSigner;
        this.retrySchedule = retrySchedule;
        this.httpClient = httpClient;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 10;
    }

    /**
     * Executes one delivery attempt for the given claimed delivery.
     * Updates delivery status, timestamps, and error summaries safely in its own transaction.
     *
     * @param delivery the delivery to dispatch
     */
    @Transactional
    public void dispatchDelivery(WebhookDelivery delivery) {
        Instant now = Instant.now();
        int attemptNumber = delivery.getAttemptCount() + 1;
        delivery.setAttemptCount(attemptNumber);
        delivery.setLastAttemptAt(now);

        MerchantWebhookConfig config = configRepository.findById(delivery.getMerchantId()).orElse(null);
        if (config == null || !config.isEnabled()) {
            handleFailedAttempt(delivery, null, "Webhook configuration missing or disabled", now, attemptNumber);
            return;
        }

        // Re-validate target URL against SSRF rules at send time
        try {
            ssrfValidator.validateUrl(config.getUrl());
        } catch (Exception e) {
            handleFailedAttempt(delivery, null, "SSRF validation failed: " + e.getMessage(), now, attemptNumber);
            return;
        }

        String secret;
        try {
            secret = secretCipher.decrypt(config.getSecretEncrypted());
        } catch (Exception e) {
            handleFailedAttempt(delivery, null, "Failed to decrypt webhook secret", now, attemptNumber);
            return;
        }

        long timestampSeconds = now.getEpochSecond();
        String signature = hmacSigner.sign(timestampSeconds, delivery.getPayload(), secret);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getUrl()))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .header("X-SwiftDrop-Event", delivery.getEventType())
                    .header("X-SwiftDrop-Delivery", delivery.getEventId().toString())
                    .header("X-SwiftDrop-Attempt", String.valueOf(attemptNumber))
                    .header("X-SwiftDrop-Timestamp", String.valueOf(timestampSeconds))
                    .header("X-SwiftDrop-Signature", signature)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(delivery.getPayload(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int statusCode = response.statusCode();

            if (statusCode >= 200 && statusCode < 300) {
                // Success
                delivery.setStatus(WebhookDeliveryStatus.SUCCEEDED);
                delivery.setLastHttpStatus(statusCode);
                delivery.setLastError(null);
                delivery.setCompletedAt(Instant.now());
                delivery.setNextAttemptAt(null);
                deliveryRepository.save(delivery);
                log.info("Webhook delivery {} for merchant {} succeeded with HTTP {}",
                        delivery.getEventId(), delivery.getMerchantId(), statusCode);
            } else {
                // Non-2xx is a failed attempt
                handleFailedAttempt(delivery, statusCode, "HTTP " + statusCode, now, attemptNumber);
            }
        } catch (HttpTimeoutException e) {
            handleFailedAttempt(delivery, null, "Timeout after " + timeoutSeconds + "s", now, attemptNumber);
        } catch (Exception e) {
            handleFailedAttempt(delivery, null, "Connection error: " + e.getClass().getSimpleName(), now, attemptNumber);
        }
    }

    private void handleFailedAttempt(WebhookDelivery delivery, Integer httpStatus, String errorMessage,
                                     Instant attemptTime, int attemptNumber) {
        delivery.setLastHttpStatus(httpStatus);
        delivery.setLastError(truncateError(errorMessage));

        if (retrySchedule.isTerminalFailure(attemptNumber)) {
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
            delivery.setCompletedAt(Instant.now());
            delivery.setNextAttemptAt(null);
            log.warn("Webhook delivery {} permanently FAILED after {} attempts (last error: {})",
                    delivery.getEventId(), attemptNumber, delivery.getLastError());
        } else {
            delivery.setStatus(WebhookDeliveryStatus.PENDING);
            Instant nextAttempt = retrySchedule.calculateNextAttempt(attemptTime, attemptNumber);
            delivery.setNextAttemptAt(nextAttempt);
            log.info("Webhook delivery {} attempt {} failed ({}); scheduled retry at {}",
                    delivery.getEventId(), attemptNumber, delivery.getLastError(), nextAttempt);
        }
        deliveryRepository.save(delivery);
    }

    private static String truncateError(String error) {
        if (error == null) {
            return null;
        }
        if (error.length() > MAX_ERROR_LENGTH) {
            return error.substring(0, MAX_ERROR_LENGTH);
        }
        return error;
    }
}


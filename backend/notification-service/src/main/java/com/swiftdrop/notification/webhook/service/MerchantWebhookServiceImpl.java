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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link MerchantWebhookService}.
 */
@Service
public class MerchantWebhookServiceImpl implements MerchantWebhookService {

    private static final Logger log = LoggerFactory.getLogger(MerchantWebhookServiceImpl.class);

    private final MerchantWebhookConfigRepository configRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookEnqueueService enqueueService;
    private final SsrfValidator ssrfValidator;
    private final SecretCipher secretCipher;
    private final HmacSigner hmacSigner;
    private final WebhookMapper webhookMapper;

    public MerchantWebhookServiceImpl(MerchantWebhookConfigRepository configRepository,
                                      WebhookDeliveryRepository deliveryRepository,
                                      WebhookEnqueueService enqueueService,
                                      SsrfValidator ssrfValidator,
                                      SecretCipher secretCipher,
                                      HmacSigner hmacSigner,
                                      WebhookMapper webhookMapper) {
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
        this.deliveryRepository = Objects.requireNonNull(deliveryRepository, "deliveryRepository");
        this.enqueueService = Objects.requireNonNull(enqueueService, "enqueueService");
        this.ssrfValidator = Objects.requireNonNull(ssrfValidator, "ssrfValidator");
        this.secretCipher = Objects.requireNonNull(secretCipher, "secretCipher");
        this.hmacSigner = Objects.requireNonNull(hmacSigner, "hmacSigner");
        this.webhookMapper = Objects.requireNonNull(webhookMapper, "webhookMapper");
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantWebhookConfigResponse getConfig(UUID merchantId) {
        Objects.requireNonNull(merchantId, "merchantId");
        return configRepository.findById(merchantId)
                .map(cfg -> MerchantWebhookConfigResponse.withoutSecret(cfg.getUrl(), cfg.isEnabled(), cfg.getSecretEncrypted() != null))
                .orElseGet(MerchantWebhookConfigResponse::unconfigured);
    }

    @Override
    @Transactional
    public MerchantWebhookConfigResponse saveConfig(UUID merchantId, MerchantWebhookConfigRequest request) {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(request, "request");

        String targetUrl = request.url() != null ? request.url().trim() : "";
        ssrfValidator.validateUrl(targetUrl);

        boolean enabled = request.enabled() == null || request.enabled();
        Optional<MerchantWebhookConfig> existingOpt = configRepository.findById(merchantId);

        if (existingOpt.isEmpty()) {
            // First save: generate secret, encrypt at rest, return secret once
            String plainSecret = hmacSigner.generateSecret();
            byte[] encryptedSecret = secretCipher.encrypt(plainSecret);

            MerchantWebhookConfig newConfig = new MerchantWebhookConfig(merchantId, targetUrl, encryptedSecret, enabled);
            configRepository.save(newConfig);
            log.info("Initialized webhook configuration for merchant {}", merchantId);
            return MerchantWebhookConfigResponse.withSecret(targetUrl, enabled, true, plainSecret);
        } else {
            // Later save: update URL and enabled status; keep existing secret and never return it
            MerchantWebhookConfig existing = existingOpt.get();
            existing.setUrl(targetUrl);
            existing.setEnabled(enabled);
            configRepository.save(existing);
            log.info("Updated webhook configuration for merchant {}", merchantId);
            return MerchantWebhookConfigResponse.withoutSecret(targetUrl, enabled, true);
        }
    }

    @Override
    @Transactional
    public RotateSecretResponse rotateSecret(UUID merchantId) {
        Objects.requireNonNull(merchantId, "merchantId");

        MerchantWebhookConfig config = configRepository.findById(merchantId)
                .orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_ERROR,
                        "Cannot rotate secret without a saved webhook configuration"));

        String newSecret = hmacSigner.generateSecret();
        config.setSecretEncrypted(secretCipher.encrypt(newSecret));
        configRepository.save(config);
        log.info("Rotated webhook secret for merchant {}", merchantId);

        return new RotateSecretResponse(newSecret);
    }

    @Override
    @Transactional
    public WebhookDeliveryItemResponse triggerTest(UUID merchantId) {
        Objects.requireNonNull(merchantId, "merchantId");

        MerchantWebhookConfig config = configRepository.findById(merchantId)
                .orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_ERROR,
                        "Cannot send test webhook without a saved configuration"));

        WebhookDelivery testDelivery = enqueueService.enqueueTestPing(merchantId, config.getUrl());
        log.info("Enqueued test webhook ping for merchant {}", merchantId);
        return webhookMapper.toItemResponse(testDelivery);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagination<WebhookDeliveryItemResponse> listDeliveries(UUID merchantId, WebhookDeliveryStatus status, Pageable pageable) {
        Objects.requireNonNull(merchantId, "merchantId");

        Page<WebhookDelivery> paged;
        if (status != null) {
            paged = deliveryRepository.findByMerchantIdAndStatus(merchantId, status, pageable);
        } else {
            paged = deliveryRepository.findByMerchantId(merchantId, pageable);
        }

        List<WebhookDeliveryItemResponse> items = paged.getContent().stream()
                .map(webhookMapper::toItemResponse)
                .toList();

        return Pagination.of(items, paged.getNumber(), paged.getSize(), paged.getTotalElements());
    }

    @Override
    @Transactional
    public WebhookDeliveryItemResponse retryDelivery(UUID merchantId, UUID deliveryId) {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(deliveryId, "deliveryId");

        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Webhook delivery not found"));

        if (!merchantId.equals(delivery.getMerchantId())) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Webhook delivery not found");
        }

        if (delivery.getStatus() != WebhookDeliveryStatus.FAILED) {
            throw new DomainException(ErrorCode.INVALID_STATUS_TRANSITION, "Only FAILED deliveries can be retried");
        }

        delivery.setStatus(WebhookDeliveryStatus.PENDING);
        delivery.setAttemptCount(0);
        delivery.setNextAttemptAt(Instant.now());
        delivery.setLastError(null);
        delivery.setLastHttpStatus(null);
        delivery.setCompletedAt(null);

        WebhookDelivery updated = deliveryRepository.save(delivery);
        log.info("Re-queued failed webhook delivery {} for merchant {}", deliveryId, merchantId);
        return webhookMapper.toItemResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public WebhookSummaryResponse getSummary(UUID merchantId) {
        Objects.requireNonNull(merchantId, "merchantId");

        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        long pending = deliveryRepository.countByMerchantIdAndStatus(merchantId, WebhookDeliveryStatus.PENDING);
        long succeeded24h = deliveryRepository.countCompletedSince(merchantId, WebhookDeliveryStatus.SUCCEEDED, since);
        long failed24h = deliveryRepository.countCompletedSince(merchantId, WebhookDeliveryStatus.FAILED, since);

        return new WebhookSummaryResponse(failed24h, pending, succeeded24h);
    }
}


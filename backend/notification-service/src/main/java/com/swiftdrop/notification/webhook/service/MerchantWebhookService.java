package com.swiftdrop.notification.webhook.service;

import com.swiftdrop.common.paging.Pagination;
import com.swiftdrop.notification.webhook.dto.request.MerchantWebhookConfigRequest;
import com.swiftdrop.notification.webhook.dto.response.MerchantWebhookConfigResponse;
import com.swiftdrop.notification.webhook.dto.response.RotateSecretResponse;
import com.swiftdrop.notification.webhook.dto.response.WebhookDeliveryItemResponse;
import com.swiftdrop.notification.webhook.dto.response.WebhookSummaryResponse;
import com.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/**
 * Service for merchant webhook management (FR-WHK-001, FR-WHK-003, FR-WHK-004, FR-WHK-006).
 */
public interface MerchantWebhookService {

    MerchantWebhookConfigResponse getConfig(UUID merchantId);

    MerchantWebhookConfigResponse saveConfig(UUID merchantId, MerchantWebhookConfigRequest request);

    RotateSecretResponse rotateSecret(UUID merchantId);

    WebhookDeliveryItemResponse triggerTest(UUID merchantId);

    Pagination<WebhookDeliveryItemResponse> listDeliveries(UUID merchantId, WebhookDeliveryStatus status, Pageable pageable);

    WebhookDeliveryItemResponse retryDelivery(UUID merchantId, UUID deliveryId);

    WebhookSummaryResponse getSummary(UUID merchantId);
}


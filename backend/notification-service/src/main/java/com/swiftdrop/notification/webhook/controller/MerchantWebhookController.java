package com.swiftdrop.notification.webhook.controller;

import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.paging.Pagination;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.common.security.CurrentUsers;
import com.swiftdrop.notification.webhook.dto.request.MerchantWebhookConfigRequest;
import com.swiftdrop.notification.webhook.dto.response.MerchantWebhookConfigResponse;
import com.swiftdrop.notification.webhook.dto.response.RotateSecretResponse;
import com.swiftdrop.notification.webhook.dto.response.WebhookDeliveryItemResponse;
import com.swiftdrop.notification.webhook.dto.response.WebhookSummaryResponse;
import com.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.swiftdrop.notification.webhook.service.MerchantWebhookService;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for merchant webhook configuration and delivery log (FR-WHK-001, FR-WHK-003 to 006).
 */
@RestController
@RequestMapping("/api/v1/merchant/webhook")
@PreAuthorize("hasRole('MERCHANT')")
public class MerchantWebhookController {

    private final MerchantWebhookService webhookService;

    public MerchantWebhookController(MerchantWebhookService webhookService) {
        this.webhookService = Objects.requireNonNull(webhookService, "webhookService");
    }

    @GetMapping
    public ResponseEntity<MerchantWebhookConfigResponse> getConfig() {
        UUID merchantId = getMerchantId();
        return ResponseEntity.ok(webhookService.getConfig(merchantId));
    }

    @PutMapping
    public ResponseEntity<MerchantWebhookConfigResponse> saveConfig(
            @Valid @RequestBody MerchantWebhookConfigRequest request) {
        UUID merchantId = getMerchantId();
        return ResponseEntity.ok(webhookService.saveConfig(merchantId, request));
    }

    @PostMapping("/rotate-secret")
    public ResponseEntity<RotateSecretResponse> rotateSecret() {
        UUID merchantId = getMerchantId();
        return ResponseEntity.ok(webhookService.rotateSecret(merchantId));
    }

    @PostMapping("/test")
    public ResponseEntity<WebhookDeliveryItemResponse> testWebhook() {
        UUID merchantId = getMerchantId();
        return ResponseEntity.ok(webhookService.triggerTest(merchantId));
    }

    @GetMapping("/deliveries")
    public ResponseEntity<Pagination<WebhookDeliveryItemResponse>> listDeliveries(
            @RequestParam(required = false) WebhookDeliveryStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID merchantId = getMerchantId();
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.min(Math.max(1, size), 100);
        Pageable pageable = PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(webhookService.listDeliveries(merchantId, status, pageable));
    }

    @PostMapping("/deliveries/{id}/retry")
    public ResponseEntity<WebhookDeliveryItemResponse> retryDelivery(@PathVariable UUID id) {
        UUID merchantId = getMerchantId();
        return ResponseEntity.ok(webhookService.retryDelivery(merchantId, id));
    }

    @GetMapping("/summary")
    public ResponseEntity<WebhookSummaryResponse> getSummary() {
        UUID merchantId = getMerchantId();
        return ResponseEntity.ok(webhookService.getSummary(merchantId));
    }

    private UUID getMerchantId() {
        CurrentUser user = CurrentUsers.get();
        if (user == null || user.mid() == null) {
            throw new DomainException(ErrorCode.ACCESS_DENIED, "Authenticated user is not linked to a merchant account");
        }
        return user.mid();
    }
}

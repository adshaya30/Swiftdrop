package com.swiftdrop.notification.webhook.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for saving or updating merchant webhook configuration (FR-WHK-001).
 */
public record MerchantWebhookConfigRequest(
        @NotBlank(message = "URL must not be blank")
        @Size(max = 500, message = "URL must not exceed 500 characters")
        String url,

        Boolean enabled
) {}


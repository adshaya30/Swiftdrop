package com.swiftdrop.notification.webhook.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response for merchant webhook configuration (FR-WHK-001).
 * Secret is included ONLY on initial generation or rotation; never on GET.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MerchantWebhookConfigResponse(
        String url,
        boolean enabled,
        boolean hasSecret,
        String secret
) {
    public static MerchantWebhookConfigResponse unconfigured() {
        return new MerchantWebhookConfigResponse(null, false, false, null);
    }

    public static MerchantWebhookConfigResponse withoutSecret(String url, boolean enabled, boolean hasSecret) {
        return new MerchantWebhookConfigResponse(url, enabled, hasSecret, null);
    }

    public static MerchantWebhookConfigResponse withSecret(String url, boolean enabled, boolean hasSecret, String secret) {
        return new MerchantWebhookConfigResponse(url, enabled, hasSecret, secret);
    }
}


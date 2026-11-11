package com.swiftdrop.notification.webhook.dto.response;

/**
 * Response for rotating merchant webhook secret (FR-WHK-003).
 */
public record RotateSecretResponse(
        String secret
) {}


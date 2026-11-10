package com.swiftdrop.auth.merchant.dto;

import com.swiftdrop.common.enums.CredentialStatus;
import java.time.Instant;
import java.util.UUID;

/** One entry of GET /api/v1/merchant/credentials: never contains the secret or its hash (FR-AUTH-018). */
public record CredentialSummary(
        UUID id,
        String clientId,
        String label,
        CredentialStatus status,
        Instant createdAt,
        Instant lastUsedAt) {
}

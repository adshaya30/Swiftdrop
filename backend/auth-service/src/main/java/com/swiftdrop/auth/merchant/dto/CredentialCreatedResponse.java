package com.swiftdrop.auth.merchant.dto;

import com.swiftdrop.common.enums.CredentialStatus;
import java.time.Instant;
import java.util.UUID;

/** 201 body: the only time the client secret is ever returned (FR-AUTH-018). */
public record CredentialCreatedResponse(
        UUID id,
        String clientId,
        String clientSecret,
        String label,
        CredentialStatus status,
        Instant createdAt) {

    /** Keeps the secret out of logs. */
    @Override
    public String toString() {
        return "CredentialCreatedResponse[id=" + id + ", clientSecret=****]";
    }
}

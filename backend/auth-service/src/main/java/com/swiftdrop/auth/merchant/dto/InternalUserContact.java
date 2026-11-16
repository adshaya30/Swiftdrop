package com.swiftdrop.auth.merchant.dto;

import java.util.UUID;

/**
 * Body of GET /internal/v1/users/{id}/contact (SRS 7.4, FR-AUTH-022). Contact data: only ever returned to internal
 * callers (X-Internal-Key), never logged (BR-28).
 */
public record InternalUserContact(UUID id, String fullName, String email, String phone) {

    @Override
    public String toString() {
        return "InternalUserContact[id=" + id + "]";
    }
}

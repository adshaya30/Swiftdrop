package com.swiftdrop.auth.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/**
 * Principal representing an authenticated user derived strictly from a cryptographically verified Bearer JWT.
 * SRS v2.1 – Section 3.6, FR-AUTH-003, FR-AUTH-012, FR-AUTH-014
 */
public record AuthenticatedUser(
        UUID userId,
        String role,
        boolean mustChangePassword,
        UUID merchantId
) implements Principal, Serializable {

    @Override
    public String getName() {
        return userId.toString();
    }
}

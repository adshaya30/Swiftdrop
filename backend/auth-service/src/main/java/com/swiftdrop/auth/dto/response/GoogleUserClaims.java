package com.swiftdrop.auth.dto.response;

/**
 * Validated OpenID Connect claims extracted from a Google ID Token.
 * SRS v2.1 – Flow 4.5, FR-AUTH-009, FR-AUTH-010
 */
public record GoogleUserClaims(
        String sub,
        String email,
        String name,
        boolean emailVerified
) {}


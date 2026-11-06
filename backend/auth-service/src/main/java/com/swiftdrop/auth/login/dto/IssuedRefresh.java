package com.swiftdrop.auth.login.dto;

import com.swiftdrop.auth.domain.RefreshToken;

import java.time.Instant;
import java.util.UUID;

/**
 * Container for newly issued refresh token (raw token for cookie + DB entity).
 */
public record IssuedRefresh(
        String rawToken,
        UUID familyId,
        Instant expiresAt,
        RefreshToken entity
) {
}

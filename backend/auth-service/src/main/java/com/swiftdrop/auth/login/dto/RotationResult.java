package com.swiftdrop.auth.login.dto;

import com.swiftdrop.auth.dto.response.LoginResponse;

/**
 * Result of refresh token rotation per FR-AUTH-007 and Master Prompt 8.1.
 * If newRawRefreshToken is null, indicates concurrent-tab grace response (no new cookie issued).
 */
public record RotationResult(
        LoginResponse response,
        String newRawRefreshToken,
        boolean gracePeriodReused
) {
    public boolean hasNewRefreshToken() {
        return newRawRefreshToken != null && !newRawRefreshToken.isBlank();
    }
}

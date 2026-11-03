package com.swiftdrop.auth.dto.response;

/**
 * Result of Google OAuth callback processing.
 * SRS v2.1 – Flow 4.5, FR-AUTH-009, FR-AUTH-010
 */
public record GoogleCallbackResult(
        String redirectUrl,
        String rawRefreshToken
) {
    public boolean hasRefreshToken() {
        return rawRefreshToken != null && !rawRefreshToken.isBlank();
    }
}


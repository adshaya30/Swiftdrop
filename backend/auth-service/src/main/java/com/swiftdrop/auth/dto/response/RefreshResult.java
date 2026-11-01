package com.swiftdrop.auth.dto.response;

/**
 * Result of a refresh token rotation.
 * Contains the API response body and the optional new raw refresh token for cookie issuance.
 * If newRawRefreshToken is null, indicates concurrent-tab grace response (no new cookie sent).
 * SRS v2.1 – FR-AUTH-007
 */
public record RefreshResult(LoginResponse response, String newRawRefreshToken) {

    public boolean hasNewRefreshToken() {
        return newRawRefreshToken != null && !newRawRefreshToken.isBlank();
    }
}


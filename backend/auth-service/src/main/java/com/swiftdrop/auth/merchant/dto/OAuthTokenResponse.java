package com.swiftdrop.auth.merchant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Success body of POST /api/v1/oauth/token (RFC 6749 section 5.1, FR-AUTH-019). */
public record OAuthTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("scope") String scope) {

    /** Keeps the token out of logs. */
    @Override
    public String toString() {
        return "OAuthTokenResponse[scope=" + scope + ", accessToken=****]";
    }
}

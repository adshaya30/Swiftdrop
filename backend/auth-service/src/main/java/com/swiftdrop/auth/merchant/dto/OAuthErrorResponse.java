package com.swiftdrop.auth.merchant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Error body of the token endpoint (RFC 6749 section 5.2): not the Problem Details format. */
public record OAuthErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("error_description") String errorDescription) {
}

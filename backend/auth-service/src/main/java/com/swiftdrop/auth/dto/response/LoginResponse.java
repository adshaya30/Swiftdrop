package com.swiftdrop.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Successful response for POST /api/v1/auth/login.
 * SRS v2.1 – FR-AUTH-003
 */
public class LoginResponse {

    @JsonProperty("accessToken")
    private String accessToken;

    @JsonProperty("tokenType")
    private String tokenType = "Bearer";

    @JsonProperty("expiresIn")
    private long expiresIn = 900;

    @JsonProperty("user")
    private LoginUserSummary user;

    public LoginResponse() {}

    public LoginResponse(String accessToken, long expiresIn, LoginUserSummary user) {
        this.accessToken = accessToken;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public String getAccessToken() { return accessToken; }
    public String getTokenType() { return tokenType; }
    public long getExpiresIn() { return expiresIn; }
    public LoginUserSummary getUser() { return user; }
}


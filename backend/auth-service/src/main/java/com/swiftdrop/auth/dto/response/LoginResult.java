package com.swiftdrop.auth.dto.response;

/**
 * Internal container for login response data and the raw refresh token
 * to be dispatched in the HttpOnly cookie.
 */
public class LoginResult {

    private final LoginResponse response;
    private final String rawRefreshToken;

    public LoginResult(LoginResponse response, String rawRefreshToken) {
        this.response = response;
        this.rawRefreshToken = rawRefreshToken;
    }

    public LoginResponse getResponse() {
        return response;
    }

    public String getRawRefreshToken() {
        return rawRefreshToken;
    }
}


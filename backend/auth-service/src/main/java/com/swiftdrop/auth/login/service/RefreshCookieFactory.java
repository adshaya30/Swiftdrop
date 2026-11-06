package com.swiftdrop.auth.login.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Creates and clears the HttpOnly {@code sd_refresh} cookie per FR-AUTH-007, FR-AUTH-008.
 * Attributes:
 * - Name: sd_refresh
 * - HttpOnly: true
 * - Secure: configurable via COOKIE_SECURE (defaults to false for localhost dev, true in prod)
 * - SameSite: Strict
 * - Path: /api/v1/auth
 * - Max-Age: 7 days (or 0 for clear)
 */
@Component
public class RefreshCookieFactory {

    public static final String COOKIE_NAME = "sd_refresh";
    public static final String COOKIE_PATH = "/api/v1/auth";
    public static final Duration MAX_AGE = Duration.ofDays(7);

    private final boolean secure;

    public RefreshCookieFactory(@Value("${cookie.secure:${COOKIE_SECURE:false}}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie create(String rawRefreshToken) {
        return ResponseCookie.from(COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(MAX_AGE)
                .build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
    }
}

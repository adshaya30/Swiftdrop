package com.swiftdrop.auth.google.controller;

import com.swiftdrop.auth.google.dto.AuthConfigResponse;
import com.swiftdrop.auth.google.dto.GoogleLoginInitResult;
import com.swiftdrop.auth.google.service.GoogleOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;

/**
 * Endpoints for Google OAuth 2.0 PKCE authentication flow.
 * SRS v2.1 – Flow 4.5, FR-AUTH-009, FR-AUTH-010, FR-AUTH-011
 */
@RestController
@RequestMapping("/api/v1/auth")
public class GoogleAuthController {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthController.class);
    private static final String OAUTH_COOKIE_NAME = "sd_oauth";
    private static final String OAUTH_COOKIE_PATH = "/api/v1/auth/google";

    private final GoogleOAuthService googleOAuthService;

    public GoogleAuthController(GoogleOAuthService googleOAuthService) {
        this.googleOAuthService = googleOAuthService;
    }

    /**
     * GET /api/v1/auth/config
     * Returns feature configuration indicating whether Google authentication is enabled.
     */
    @GetMapping("/config")
    public ResponseEntity<AuthConfigResponse> getConfig() {
        return ResponseEntity.ok(new AuthConfigResponse(googleOAuthService.isGoogleEnabled()));
    }

    /**
     * GET /api/v1/auth/google/login
     * Initiates Google OAuth 2.0 PKCE flow, sets sd_oauth cookie, and redirects to Google.
     */
    @GetMapping("/google/login")
    public ResponseEntity<Void> login() {
        if (!googleOAuthService.isGoogleEnabled()) {
            log.warn("Google login requested but Google OAuth is disabled");
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(googleOAuthService.getPublicBaseUrl() + "/login?error=google_failed"))
                    .build();
        }

        try {
            GoogleLoginInitResult initResult = googleOAuthService.initiateLogin();

            ResponseCookie oauthCookie = ResponseCookie.from(OAUTH_COOKIE_NAME, initResult.rawState())
                    .httpOnly(true)
                    .secure(googleOAuthService.isCookieSecure())
                    .sameSite("Lax")
                    .path(OAUTH_COOKIE_PATH)
                    .maxAge(Duration.ofMinutes(5))
                    .build();

            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.SET_COOKIE, oauthCookie.toString())
                    .location(URI.create(initResult.authorizationUrl()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to initiate Google login: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(googleOAuthService.getPublicBaseUrl() + "/login?error=google_failed"))
                    .build();
        }
    }

    /**
     * GET /api/v1/auth/google/callback
     * Handles authorization code callback from Google.
     */
    @GetMapping("/google/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            @CookieValue(name = OAUTH_COOKIE_NAME, required = false) String cookieState) {

        // Build clear cookie for sd_oauth
        ResponseCookie clearOauthCookie = ResponseCookie.from(OAUTH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(googleOAuthService.isCookieSecure())
                .sameSite("Lax")
                .path(OAUTH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();

        ResponseCookie clearRootRefreshCookie = ResponseCookie.from("sd_refresh", "")
                .httpOnly(true)
                .secure(googleOAuthService.isCookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        if (error != null && !error.isBlank()) {
            String errorParam = "access_denied".equalsIgnoreCase(error) ? "google_denied" : "google_failed";
            log.warn("Google returned OAuth error: {}", error);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.SET_COOKIE, clearOauthCookie.toString())
                    .location(URI.create(googleOAuthService.getPublicBaseUrl() + "/login?error=" + errorParam))
                    .build();
        }

        GoogleOAuthService.CallbackOutcome outcome = googleOAuthService.handleCallback(code, state, cookieState);

        var responseBuilder = ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, clearOauthCookie.toString())
                .header(HttpHeaders.SET_COOKIE, clearRootRefreshCookie.toString())
                .location(URI.create(outcome.redirectUrl()));

        if (outcome.refreshCookie() != null) {
            responseBuilder.header(HttpHeaders.SET_COOKIE, outcome.refreshCookie().toString());
        }

        return responseBuilder.build();
    }
}


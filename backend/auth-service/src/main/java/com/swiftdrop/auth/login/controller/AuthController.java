package com.swiftdrop.auth.login.controller;

import com.swiftdrop.auth.dto.request.LoginRequest;
import com.swiftdrop.auth.dto.request.RegistrationRequest;
import com.swiftdrop.auth.dto.response.*;
import com.swiftdrop.auth.exception.InvalidHeaderException;
import com.swiftdrop.auth.exception.UnauthenticatedException;
import com.swiftdrop.auth.login.dto.RotationResult;
import com.swiftdrop.auth.login.service.LoginService;
import com.swiftdrop.auth.login.service.RefreshCookieFactory;
import com.swiftdrop.auth.login.service.RefreshTokenService;
import com.swiftdrop.auth.service.GoogleOAuthService;
import com.swiftdrop.auth.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;

/**
 * Public authentication endpoints adhering to SRS v2.1 FR-AUTH-001 through FR-AUTH-011
 * and Master Prompt Section 8.1.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REQUIRED_HEADER_NAME = "X-Requested-With";
    private static final String REQUIRED_HEADER_VALUE = "SwiftDrop";
    private static final String OAUTH_COOKIE_NAME = "sd_oauth";
    private static final String OAUTH_COOKIE_PATH = "/api/v1/auth/google";

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final GoogleOAuthService googleOAuthService;

    public AuthController(
            RegistrationService registrationService,
            LoginService loginService,
            RefreshTokenService refreshTokenService,
            RefreshCookieFactory refreshCookieFactory,
            GoogleOAuthService googleOAuthService) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.refreshTokenService = refreshTokenService;
        this.refreshCookieFactory = refreshCookieFactory;
        this.googleOAuthService = googleOAuthService;
    }

    /**
     * GET /api/v1/auth/config (FR-AUTH-011)
     */
    @GetMapping("/config")
    public ResponseEntity<AuthConfigResponse> getConfig() {
        return ResponseEntity.ok(new AuthConfigResponse(googleOAuthService.isGoogleEnabled()));
    }

    /**
     * GET /api/v1/auth/google/login (Flow 4.5)
     */
    @GetMapping("/google/login")
    public ResponseEntity<Void> googleLogin() {
        GoogleLoginInitResult result = googleOAuthService.initiateLogin();
        ResponseCookie oauthCookie = buildOAuthCookie(result.state(), Duration.ofMinutes(5));

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(result.authorizationUrl()))
                .header(HttpHeaders.SET_COOKIE, oauthCookie.toString())
                .build();
    }

    /**
     * GET /api/v1/auth/google/callback (Flow 4.5, FR-AUTH-009, FR-AUTH-010)
     */
    @GetMapping("/google/callback")
    public ResponseEntity<Void> googleCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @CookieValue(value = OAUTH_COOKIE_NAME, required = false) String cookieState,
            HttpServletRequest servletRequest) {

        String ip = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);

        GoogleCallbackResult result = googleOAuthService.handleCallback(
                code, state, cookieState, error, userAgent, ip
        );

        ResponseCookie clearOAuthCookie = buildOAuthCookie("", Duration.ZERO);
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(result.redirectUrl()))
                .header(HttpHeaders.SET_COOKIE, clearOAuthCookie.toString());

        if (result.hasRefreshToken()) {
            ResponseCookie refreshCookie = refreshCookieFactory.create(result.rawRefreshToken());
            responseBuilder.header(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        }

        return responseBuilder.build();
    }

    /**
     * POST /api/v1/auth/register (FR-AUTH-001)
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegistrationRequest request) {
        UserResponse response = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/auth/login (FR-AUTH-003, FR-AUTH-004, FR-AUTH-005)
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {

        String ip = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);

        LoginResult result = loginService.login(request, ip, userAgent);
        ResponseCookie refreshCookie = refreshCookieFactory.create(result.getRawRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.getResponse());
    }

    /**
     * POST /api/v1/auth/refresh (FR-AUTH-007)
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @RequestHeader(value = REQUIRED_HEADER_NAME, required = false) String xRequestedWith,
            @CookieValue(value = RefreshCookieFactory.COOKIE_NAME, required = false) String rawRefreshToken,
            HttpServletRequest servletRequest) {

        validateXRequestedWithHeader(xRequestedWith);

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthenticatedException("Refresh token is missing");
        }

        String ip = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);

        RotationResult result = refreshTokenService.rotate(rawRefreshToken, userAgent, ip);

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();
        if (result.hasNewRefreshToken()) {
            ResponseCookie newCookie = refreshCookieFactory.create(result.newRawRefreshToken());
            responseBuilder.header(HttpHeaders.SET_COOKIE, newCookie.toString());
        }

        return responseBuilder.body(result.response());
    }

    /**
     * POST /api/v1/auth/logout (FR-AUTH-008)
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = REQUIRED_HEADER_NAME, required = false) String xRequestedWith,
            @CookieValue(value = RefreshCookieFactory.COOKIE_NAME, required = false) String rawRefreshToken) {

        validateXRequestedWithHeader(xRequestedWith);

        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revokeFamily(rawRefreshToken);
        }

        ResponseCookie clearCookie = refreshCookieFactory.clear();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    private void validateXRequestedWithHeader(String headerValue) {
        if (headerValue == null || !headerValue.equalsIgnoreCase(REQUIRED_HEADER_VALUE)) {
            throw new InvalidHeaderException("Header '" + REQUIRED_HEADER_NAME + ": " + REQUIRED_HEADER_VALUE + "' is required");
        }
    }

    private ResponseCookie buildOAuthCookie(String value, Duration maxAge) {
        return ResponseCookie.from(OAUTH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(false) // Matches dev/prod behavior
                .sameSite("Lax")
                .path(OAUTH_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

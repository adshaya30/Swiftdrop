package com.swiftdrop.auth.controller;

import com.swiftdrop.auth.dto.request.LoginRequest;
import com.swiftdrop.auth.dto.request.RegistrationRequest;
import com.swiftdrop.auth.dto.response.LoginResponse;
import com.swiftdrop.auth.dto.response.LoginResult;
import com.swiftdrop.auth.dto.response.RefreshResult;
import com.swiftdrop.auth.dto.response.UserResponse;
import com.swiftdrop.auth.exception.InvalidHeaderException;
import com.swiftdrop.auth.exception.UnauthenticatedException;
import com.swiftdrop.auth.service.LoginService;
import com.swiftdrop.auth.service.RefreshTokenService;
import com.swiftdrop.auth.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * Public authentication endpoints.
 * SRS v2.1 – FR-AUTH-001, FR-AUTH-002, FR-AUTH-003, FR-AUTH-004, FR-AUTH-005, FR-AUTH-007, FR-AUTH-008
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REQUIRED_HEADER_NAME = "X-Requested-With";
    private static final String REQUIRED_HEADER_VALUE = "SwiftDrop";
    private static final String REFRESH_COOKIE_NAME = "sd_refresh";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final boolean cookieSecure;

    public AuthController(
            RegistrationService registrationService,
            LoginService loginService,
            RefreshTokenService refreshTokenService,
            @Value("${auth.cookie.secure:${COOKIE_SECURE:false}}") boolean cookieSecure) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.refreshTokenService = refreshTokenService;
        this.cookieSecure = cookieSecure;
    }

    /**
     * POST /api/v1/auth/register
     *
     * <p>Registers a new customer account.
     * Does NOT issue JWT or refresh tokens (registration != login).</p>
     *
     * @param request validated registration payload
     * @return 201 Created with safe user summary
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegistrationRequest request) {
        UserResponse response = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/auth/login
     *
     * <p>Authenticates user using email and password, issuing RS256 JWT access token
     * and sd_refresh HttpOnly cookie.</p>
     *
     * @param request        validated login credentials
     * @param servletRequest incoming HTTP request for IP / User-Agent extraction
     * @return 200 OK with access token and user summary, plus sd_refresh cookie
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {

        String ip = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);

        LoginResult result = loginService.login(request, ip, userAgent);

        ResponseCookie refreshCookie = buildRefreshCookie(result.getRawRefreshToken(), Duration.ofDays(7));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.getResponse());
    }

    /**
     * POST /api/v1/auth/refresh
     *
     * <p>Performs Refresh Token Rotation (FR-AUTH-007).
     * Requires header X-Requested-With: SwiftDrop.
     * Reads opaque token strictly from sd_refresh HttpOnly cookie.</p>
     *
     * @param xRequestedWith header value
     * @param rawRefreshToken cookie value
     * @param servletRequest incoming HTTP request for IP / User-Agent extraction
     * @return 200 OK with fresh RS256 access JWT and rotated cookie (unless within concurrent grace window)
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @RequestHeader(value = REQUIRED_HEADER_NAME, required = false) String xRequestedWith,
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String rawRefreshToken,
            HttpServletRequest servletRequest) {

        validateXRequestedWithHeader(xRequestedWith);

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthenticatedException("Refresh token is missing");
        }

        String ip = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);

        RefreshResult result = refreshTokenService.rotate(rawRefreshToken, userAgent, ip);

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();

        // Only send a new cookie if a new refresh token was created (not in concurrent grace window)
        if (result.hasNewRefreshToken()) {
            ResponseCookie newCookie = buildRefreshCookie(result.newRawRefreshToken(), Duration.ofDays(7));
            responseBuilder.header(HttpHeaders.SET_COOKIE, newCookie.toString());
        }

        return responseBuilder.body(result.response());
    }

    /**
     * POST /api/v1/auth/logout
     *
     * <p>Invalidates the current session family and clears the sd_refresh cookie (FR-AUTH-008).
     * Requires header X-Requested-With: SwiftDrop.
     * Returns 204 No Content even if no cookie or invalid cookie was supplied.</p>
     *
     * @param xRequestedWith header value
     * @param rawRefreshToken cookie value (optional)
     * @return 204 No Content with cleared sd_refresh cookie
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = REQUIRED_HEADER_NAME, required = false) String xRequestedWith,
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String rawRefreshToken) {

        validateXRequestedWithHeader(xRequestedWith);

        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.logout(rawRefreshToken);
        }

        ResponseCookie clearCookie = buildRefreshCookie("", Duration.ZERO);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .build();
    }

    private void validateXRequestedWithHeader(String headerValue) {
        if (headerValue == null || !headerValue.equalsIgnoreCase(REQUIRED_HEADER_VALUE)) {
            throw new InvalidHeaderException("Header '" + REQUIRED_HEADER_NAME + ": " + REQUIRED_HEADER_VALUE + "' is required");
        }
    }

    private ResponseCookie buildRefreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
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

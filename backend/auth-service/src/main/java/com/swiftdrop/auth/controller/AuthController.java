package com.swiftdrop.auth.controller;

import com.swiftdrop.auth.dto.request.LoginRequest;
import com.swiftdrop.auth.dto.request.RegistrationRequest;
import com.swiftdrop.auth.dto.response.LoginResponse;
import com.swiftdrop.auth.dto.response.LoginResult;
import com.swiftdrop.auth.dto.response.UserResponse;
import com.swiftdrop.auth.service.LoginService;
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
 * SRS v2.1 – FR-AUTH-001, FR-AUTH-002, FR-AUTH-003, FR-AUTH-004, FR-AUTH-005, FR-AUTH-007
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final boolean cookieSecure;

    public AuthController(
            RegistrationService registrationService,
            LoginService loginService,
            @Value("${auth.cookie.secure:${COOKIE_SECURE:false}}") boolean cookieSecure) {
        this.registrationService = registrationService;
        this.loginService = loginService;
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

        ResponseCookie refreshCookie = ResponseCookie.from("sd_refresh", result.getRawRefreshToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(7))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.getResponse());
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

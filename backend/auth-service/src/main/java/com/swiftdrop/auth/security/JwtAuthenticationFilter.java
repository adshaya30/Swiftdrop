package com.swiftdrop.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.login.service.JwtService;
import com.swiftdrop.common.security.SecurityRules;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * Filter that validates incoming Bearer JWT tokens against RS256 public key.
 * Enforces token structure, claims integrity, user-type restriction, and forced password change.
 * SRS v2.1 – Section 3.6, FR-AUTH-003, FR-AUTH-006, FR-AUTH-012, FR-AUTH-014
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String REQUIRED_ISSUER = "swiftdrop";
    private static final String REQUIRED_AUDIENCE = "swiftdrop-api";
    private static final String REQUIRED_TYPE = "user";

    private final JwtService jwtService;
    private final SecurityRules securityRules;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, SecurityRules securityRules, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.securityRules = securityRules;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawToken = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (rawToken.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        Map<String, Object> claims;
        try {
            claims = jwtService.parseAndVerifyToken(rawToken);
        } catch (Exception e) {
            log.warn("Bearer token verification failed for {}: {}", request.getRequestURI(), e.getMessage());
            writeProblemDetails(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid, expired, or untrusted access token", request.getRequestURI());
            return;
        }

        // Validate issuer and audience
        String iss = (String) claims.get("iss");
        String aud = (String) claims.get("aud");
        if (!REQUIRED_ISSUER.equals(iss) || !REQUIRED_AUDIENCE.equals(aud)) {
            log.warn("JWT issuer/audience mismatch: iss={}, aud={}", iss, aud);
            writeProblemDetails(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token issuer or audience is invalid", request.getRequestURI());
            return;
        }

        // Validate expiration
        Number expNumber = (Number) claims.get("exp");
        if (expNumber == null || expNumber.longValue() < Instant.now().getEpochSecond()) {
            log.warn("JWT is expired");
            writeProblemDetails(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Access token has expired", request.getRequestURI());
            return;
        }

        // Validate token type
        String typ = (String) claims.get("typ");
        if ("client".equalsIgnoreCase(typ)) {
            log.warn("Client-credentials token rejected from user endpoint at {}", request.getRequestURI());
            writeProblemDetails(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Client-credentials tokens cannot access user profile endpoints", request.getRequestURI());
            return;
        }
        if (!REQUIRED_TYPE.equals(typ)) {
            log.warn("Invalid token type: {}", typ);
            writeProblemDetails(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid token type", request.getRequestURI());
            return;
        }

        // Extract subject (userId)
        String sub = (String) claims.get("sub");
        UUID userId;
        try {
            userId = UUID.fromString(sub);
        } catch (Exception e) {
            log.warn("Invalid subject UUID in token: {}", sub);
            writeProblemDetails(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid user subject in token", request.getRequestURI());
            return;
        }

        // Extract role
        String role = (String) claims.get("role");
        if (role == null || role.isBlank()) {
            log.warn("Missing role claim in token");
            writeProblemDetails(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing role claim in token", request.getRequestURI());
            return;
        }

        // Extract pwc (mustChangePassword) and mid (merchantId)
        boolean mustChangePassword = Boolean.TRUE.equals(claims.get("pwc"));
        UUID merchantId = null;
        Object midObj = claims.get("mid");
        if (midObj instanceof String midStr && !midStr.isBlank()) {
            try {
                merchantId = UUID.fromString(midStr);
            } catch (Exception ignored) {
            }
        }

        // FR-AUTH-014: Forced password change enforcement
        if (mustChangePassword) {
            String uri = request.getRequestURI();
            String method = request.getMethod();
            String target = method.toUpperCase() + " " + uri;
            boolean isAllowed = securityRules.passwordChangeWhitelist().contains(target);
            if (!isAllowed) {
                log.warn("User {} blocked by forced-password-change gate on {}", userId, uri);
                writeProblemDetails(response, HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
                        "Password change required before accessing this resource.", uri);
                return;
            }
        }

        // Establish Spring Security context
        AuthenticatedUser principal = new AuthenticatedUser(userId, role, mustChangePassword, merchantId);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, rawToken, authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void writeProblemDetails(
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String detail,
            String instance) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "https://swiftdrop.com/errors/" + code.toLowerCase().replace('_', '-'));
        problem.put("title", status.getReasonPhrase());
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put("instance", instance);
        problem.put("code", code);
        problem.put("traceId", UUID.randomUUID().toString());
        problem.put("timestamp", Instant.now().toString());

        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}

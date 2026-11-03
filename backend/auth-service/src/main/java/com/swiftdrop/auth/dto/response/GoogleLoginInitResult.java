package com.swiftdrop.auth.dto.response;

/**
 * Result of initiating Google OAuth 2.0 PKCE login.
 * SRS v2.1 – Flow 4.5, FR-AUTH-009
 */
public record GoogleLoginInitResult(
        String authorizationUrl,
        String state
) {}


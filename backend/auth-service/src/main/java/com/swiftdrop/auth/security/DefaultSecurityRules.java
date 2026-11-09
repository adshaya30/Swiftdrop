package com.swiftdrop.auth.security;

import com.swiftdrop.common.security.SecurityRules;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Default implementation of SecurityRules contract per Master Prompt Section 8.1.
 */
@Component
public class DefaultSecurityRules implements SecurityRules {

    @Override
    public List<String> publicPaths() {
        return List.of(
                "POST /api/v1/auth/register",
                "POST /api/v1/auth/register-merchant",
                "POST /api/v1/auth/login",
                "POST /api/v1/auth/refresh",
                "POST /api/v1/auth/logout",
                "GET /api/v1/auth/google/login",
                "GET /api/v1/auth/google/callback",
                "GET /api/v1/auth/config",
                "POST /api/v1/oauth/token"
        );
    }

    @Override
    public List<String> passwordChangeWhitelist() {
        return List.of(
                "POST /api/v1/me/change-password",
                "GET /api/v1/me",
                "POST /api/v1/auth/logout"
        );
    }
}


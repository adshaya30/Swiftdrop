package com.swiftdrop.common.security;

import java.util.List;

/**
 * Bean each service provides. Patterns are "METHOD /api/v1/path" (Ant style paths, "*" for any method).
 */
public interface SecurityRules {

    /** Endpoints that need no token. */
    List<String> publicPaths();

    /** Endpoints a user with pwc=true may still call (FR-AUTH change-password flow). */
    default List<String> passwordChangeWhitelist() {
        return List.of();
    }
}

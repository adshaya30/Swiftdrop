package com.swiftdrop.auth.security;

import java.util.List;

/**
 * Security rules definition contract per Master Prompt Section 8.1.
 * // TODO(common): replace with com.swiftdrop.common.security.SecurityRules from common-lib once published.
 */
public interface SecurityRules {
    List<String> publicPaths();
    List<String> passwordChangeWhitelist();
}

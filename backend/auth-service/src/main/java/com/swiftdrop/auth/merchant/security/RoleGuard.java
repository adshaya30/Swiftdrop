package com.swiftdrop.auth.merchant.security;

import com.swiftdrop.auth.security.AuthenticatedUser;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;

/**
 * Role check that does not depend on method security. auth-service currently excludes common-lib's security
 * auto-configuration, so {@code @PreAuthorize} is not active there; the merchant endpoints therefore check the role
 * themselves. The role check runs before any ownership check (BR-25): wrong role gives 403 ACCESS_DENIED.
 */
public final class RoleGuard {

    private RoleGuard() {
    }

    public static void require(AuthenticatedUser user, String role) {
        if (user == null) {
            throw new DomainException(ErrorCode.UNAUTHENTICATED, "Authentication required");
        }
        if (!role.equals(user.role())) {
            throw new DomainException(ErrorCode.ACCESS_DENIED, "Access denied");
        }
    }
}

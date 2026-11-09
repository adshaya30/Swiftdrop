package com.swiftdrop.common.security;

import com.swiftdrop.common.enums.Role;
import java.util.UUID;

/** BR-25: a resource owned by someone else must be answered with 404 by the caller of this check. */
public final class OwnershipPolicy {

    private OwnershipPolicy() {
    }

    /** ADMIN always; CUSTOMER if customerId equals the user id; MERCHANT if merchantId equals mid. */
    public static boolean isOwnerOrAdmin(CurrentUser user, UUID customerId, UUID merchantId) {
        if (user == null) {
            return false;
        }
        if (user.role() == Role.ADMIN) {
            return true;
        }
        if (user.role() == Role.CUSTOMER) {
            return customerId != null && customerId.equals(user.userId());
        }
        if (user.role() == Role.MERCHANT) {
            return merchantId != null && user.mid() != null && merchantId.equals(user.mid());
        }
        return false;
    }
}

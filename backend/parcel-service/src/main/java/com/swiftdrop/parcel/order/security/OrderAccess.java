package com.swiftdrop.parcel.order.security;

import com.swiftdrop.common.enums.Role;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.common.security.CurrentUsers;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Access rule for the merchant order API (SRS 3.6, 7.3, FR-PAR-007/008): role MERCHANT; a dashboard user token may
 * use every order endpoint; a client-credentials token needs scope {@code orders:write} to create or cancel and
 * {@code orders:read} to list or read. The order owner is always the {@code mid} claim.
 *
 * <p>common-lib already refuses client tokens on every path except {@code /merchant/orders*}.
 */
@Component
public class OrderAccess {

    public static final String SCOPE_READ = "orders:read";
    public static final String SCOPE_WRITE = "orders:write";

    /** @return the merchant id (mid) of a caller allowed to read orders */
    public UUID requireRead() {
        return require(SCOPE_READ);
    }

    /** @return the merchant id (mid) of a caller allowed to create or cancel orders */
    public UUID requireWrite() {
        return require(SCOPE_WRITE);
    }

    /** FR-PAR-014: dashboard login only. @return the merchant id (mid) */
    public UUID requireDashboardUser() {
        CurrentUser user = merchant();
        if (user.isClientToken()) {
            throw denied();
        }
        return user.mid();
    }

    private UUID require(String scope) {
        CurrentUser user = merchant();
        if (user.isClientToken() && !user.scopes().contains(scope)) {
            throw denied();
        }
        return user.mid();
    }

    private static CurrentUser merchant() {
        CurrentUser user = CurrentUsers.get();
        if (user.role() != Role.MERCHANT || user.mid() == null) {
            throw denied();
        }
        return user;
    }

    private static DomainException denied() {
        return new DomainException(ErrorCode.ACCESS_DENIED, "Access denied");
    }
}

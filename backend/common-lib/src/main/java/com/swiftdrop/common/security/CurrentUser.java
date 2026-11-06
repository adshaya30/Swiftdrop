package com.swiftdrop.common.security;

import com.swiftdrop.common.enums.Role;
import java.util.Set;
import java.util.UUID;

/** The authenticated caller, read from the verified token. mid is null unless the caller is a merchant. */
public record CurrentUser(
        UUID userId,
        Role role,
        String typ,
        UUID mid,
        Set<String> scopes,
        boolean passwordChangeRequired) {

    public boolean isClientToken() {
        return JwtClaims.TYP_CLIENT.equals(typ);
    }
}

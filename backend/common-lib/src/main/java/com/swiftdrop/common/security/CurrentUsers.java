package com.swiftdrop.common.security;

import com.swiftdrop.common.enums.Role;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Access to the authenticated caller: CurrentUsers.get(). */
public final class CurrentUsers {

    private CurrentUsers() {
    }

    public static CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new DomainException(ErrorCode.UNAUTHENTICATED, "Authentication required");
        }
        return from(jwt);
    }

    public static CurrentUser from(Jwt jwt) {
        try {
            String typ = jwt.getClaimAsString(JwtClaims.TYP);
            String mid = jwt.getClaimAsString(JwtClaims.MID);
            return new CurrentUser(
                    UUID.fromString(jwt.getSubject()),
                    Role.valueOf(jwt.getClaimAsString(JwtClaims.ROLE)),
                    typ == null ? JwtClaims.TYP_USER : typ,
                    mid == null ? null : UUID.fromString(mid),
                    scopes(jwt.getClaim(JwtClaims.SCOPE)),
                    Boolean.TRUE.equals(jwt.getClaim(JwtClaims.PWC)));
        } catch (RuntimeException e) {
            throw new DomainException(ErrorCode.UNAUTHENTICATED, "Authentication required");
        }
    }

    /** The scope claim is a space separated string (RFC 6749); a JSON array is tolerated. */
    static Set<String> scopes(Object claim) {
        if (claim == null) {
            return Set.of();
        }
        if (claim instanceof Collection<?> c) {
            Set<String> out = new HashSet<>();
            c.forEach(o -> out.add(String.valueOf(o)));
            return Set.copyOf(out);
        }
        String s = claim.toString().trim();
        return s.isEmpty() ? Set.of() : Set.copyOf(Arrays.asList(s.split("\\s+")));
    }
}

package com.swiftdrop.common.security;

import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.web.ProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Token-type rule (SRS 3.6, BR-25): a typ=client token is accepted only on /api/v1/merchant/orders and below;
 * everywhere else 403 ACCESS_DENIED. Implemented once here, never per service.
 */
class ClientTokenFilter extends OncePerRequestFilter {

    static final String ORDERS_PATH = "/api/v1/merchant/orders";

    private final ProblemWriter writer;

    ClientTokenFilter(ProblemWriter writer) {
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token
                && JwtClaims.TYP_CLIENT.equals(token.getToken().getClaimAsString(JwtClaims.TYP))) {
            String path = RulePatterns.pathOf(request);
            if (!(path.equals(ORDERS_PATH) || path.startsWith(ORDERS_PATH + "/"))) {
                writer.write(response, ErrorCode.ACCESS_DENIED, "Access denied");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}

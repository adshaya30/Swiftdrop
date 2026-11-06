package com.swiftdrop.common.security;

import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.web.ProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** pwc=true: 403 PASSWORD_CHANGE_REQUIRED on every authenticated endpoint except the service whitelist. */
class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private final Supplier<List<String>> whitelist;
    private final ProblemWriter writer;

    PasswordChangeRequiredFilter(Supplier<List<String>> whitelist, ProblemWriter writer) {
        this.whitelist = whitelist;
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token
                && Boolean.TRUE.equals(token.getToken().getClaim(JwtClaims.PWC))
                && !RulePatterns.matches(whitelist.get(), request)) {
            writer.write(response, ErrorCode.PASSWORD_CHANGE_REQUIRED, "Password change required");
            return;
        }
        chain.doFilter(request, response);
    }
}

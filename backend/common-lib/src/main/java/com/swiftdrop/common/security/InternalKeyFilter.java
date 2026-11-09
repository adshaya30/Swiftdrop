package com.swiftdrop.common.security;

import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.http.ApiHeaders;
import com.swiftdrop.common.web.ProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** /internal/** is secured only by X-Internal-Key, compared in constant time. JWTs are not used here. */
class InternalKeyFilter extends OncePerRequestFilter {

    private final InternalApiKey key;
    private final ProblemWriter writer;

    InternalKeyFilter(InternalApiKey key, ProblemWriter writer) {
        this.key = key;
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(ApiHeaders.INTERNAL_KEY);
        if (supplied == null || !MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), key.bytes())) {
            writer.write(response, ErrorCode.UNAUTHENTICATED, "Authentication required");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))));
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}

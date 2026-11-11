package com.swiftdrop.tracking.security;

import com.swiftdrop.common.security.SecurityRules;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Security rules for the tracking-service.
 * Public endpoint patterns from the SRS (FR-TRK-002, FR-TRK-003):
 *   GET /api/v1/public/tracking/**
 */
@Component
public class TrackingSecurityRules implements SecurityRules {

    @Override
    public List<String> publicPaths() {
        return List.of("GET /api/v1/public/tracking/**");
    }
}

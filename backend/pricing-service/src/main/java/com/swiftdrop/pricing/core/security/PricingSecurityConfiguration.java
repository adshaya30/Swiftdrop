package com.swiftdrop.pricing.core.security;

import com.swiftdrop.common.security.SecurityRules;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Service rules only; JWT, internal-key and token-type checks belong to common-lib (SRS 3.4). */
@Configuration(proxyBeanMethods = false)
public class PricingSecurityConfiguration {
    @Bean
    public SecurityRules securityRules() {
        return new SecurityRules() {
            @Override
            public List<String> publicPaths() {
                return List.of("GET /api/v1/public/districts", "POST /api/v1/public/quotes");
            }

            @Override
            public List<String> passwordChangeWhitelist() {
                return List.of();
            }
        };
    }
}

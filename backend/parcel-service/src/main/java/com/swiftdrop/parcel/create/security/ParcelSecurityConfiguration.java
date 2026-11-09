package com.swiftdrop.parcel.create.security;

import com.swiftdrop.common.security.SecurityRules;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Service rules only; JWT, internal-key and token-type checks belong to common-lib (SRS 3.4). */
@Configuration(proxyBeanMethods = false)
public class ParcelSecurityConfiguration {
    @Bean
    public SecurityRules securityRules() {
        return new SecurityRules() {
            @Override
            public List<String> publicPaths() {
                return List.of();
            }

            @Override
            public List<String> passwordChangeWhitelist() {
                return List.of();
            }
        };
    }
}

package com.swiftdrop.auth.merchant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.security.InternalApiKey;
import com.swiftdrop.common.security.InternalKeyFilter;
import com.swiftdrop.common.web.ProblemWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * X-Internal-Key protection for the merchant slice's internal endpoints (FR-AUTH-022). auth-service currently
 * excludes common-lib's security auto-configuration, so the shared InternalKeyFilter is registered here for these
 * two paths only. It runs after Spring Security, so the endpoints stay closed until the service-wide
 * SecurityConfig lets /internal/** through; once it does, this filter still enforces the key. When auth-service
 * adopts common-lib's security chain, this class can be deleted.
 */
@Configuration(proxyBeanMethods = false)
public class InternalEndpointsConfig {

    /** Fails the start-up when INTERNAL_API_KEY is missing, under 32 characters or a placeholder. */
    @Bean
    @ConditionalOnMissingBean
    public InternalApiKey internalApiKey(Environment env) {
        return new InternalApiKey(env.getProperty("INTERNAL_API_KEY"));
    }

    @Bean
    public FilterRegistrationBean<InternalKeyFilter> merchantInternalKeyFilter(InternalApiKey key, ObjectMapper mapper) {
        FilterRegistrationBean<InternalKeyFilter> registration =
                new FilterRegistrationBean<>(new InternalKeyFilter(key, new ProblemWriter(mapper)));
        registration.addUrlPatterns("/internal/v1/users/*", "/internal/v1/merchants/*");
        return registration;
    }
}

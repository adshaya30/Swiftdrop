package com.swiftdrop.tracking.audit.support;

import com.swiftdrop.common.security.JwtPublicKeyProvider;
import com.swiftdrop.common.testsupport.TestTokens;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Test keys for the audit-log API tests (TestTokens comes from the common-lib test-jar). */
@TestConfiguration(proxyBeanMethods = false)
public class AuditTestConfiguration {
    public static final TestTokens TOKENS = TestTokens.create();

    @Bean
    @Primary
    JwtPublicKeyProvider auditTestKeys() {
        return TOKENS.publicKeyProvider();
    }
}

package com.swiftdrop.parcel.support;

import com.swiftdrop.common.security.JwtPublicKeyProvider;
import com.swiftdrop.common.testsupport.TestTokens;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class ParcelTestConfiguration {
    public static final TestTokens TOKENS = TestTokens.create();
    @Bean
    @Primary
    JwtPublicKeyProvider testKeys() { return TOKENS.publicKeyProvider(); }
}

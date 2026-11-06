package com.swiftdrop.common.testapp;

import com.swiftdrop.common.security.JwtPublicKeyProvider;
import com.swiftdrop.common.security.SecurityRules;
import com.swiftdrop.common.testsupport.TestTokens;
import java.util.List;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;

/** A bare service: only the common-lib dependency plus a SecurityRules bean. */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class, RabbitAutoConfiguration.class, FlywayAutoConfiguration.class})
public class TestApp {

    public static final TestTokens TOKENS = TestTokens.create();
    public static final TestTokens OTHER_TOKENS = TestTokens.create();
    public static final String INTERNAL_KEY = "test-internal-key-0123456789-abcdefghijklmnop";

    @Bean
    SecurityRules securityRules() {
        return new SecurityRules() {
            @Override
            public List<String> publicPaths() {
                return List.of("GET /api/v1/public/**");
            }

            @Override
            public List<String> passwordChangeWhitelist() {
                return List.of("POST /api/v1/auth/change-password");
            }
        };
    }

    @Bean
    JwtPublicKeyProvider jwtPublicKeyProvider() {
        return TOKENS.publicKeyProvider();
    }
}

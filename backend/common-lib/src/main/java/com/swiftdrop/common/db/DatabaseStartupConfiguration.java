package com.swiftdrop.common.db;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Database start-up retry (NFR-REL-003). Hikari initialization-fail-timeout is set in the common defaults. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Flyway.class)
public class DatabaseStartupConfiguration {

    @Bean
    @ConditionalOnMissingBean(FlywayMigrationStrategy.class)
    public FlywayMigrationStrategy retryingFlywayMigrationStrategy() {
        return new RetryingFlywayMigrationStrategy();
    }
}

package com.swiftdrop.pricing.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Real PostgreSQL 16 for migrations and JPA validation; Docker is mandatory, never silently skipped. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PricingTestConfiguration.class)
public abstract class AbstractPricingDatabaseTest {
    // A process-lifetime container keeps cached Spring contexts connected to the same database.
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final String INTERNAL_KEY = "foundation-tests-" + java.util.UUID.randomUUID();
    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USER", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("INTERNAL_API_KEY", () -> INTERNAL_KEY);
        // NFR-REL-001: an unavailable broker must not prevent startup or database readiness.
        registry.add("RABBITMQ_HOST", () -> "127.0.0.1");
        registry.add("RABBITMQ_PORT", () -> "1");
        registry.add("RABBITMQ_PASSWORD", () -> "test-only");
    }
}

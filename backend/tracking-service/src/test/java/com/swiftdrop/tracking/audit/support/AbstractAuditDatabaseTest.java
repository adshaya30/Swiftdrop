package com.swiftdrop.tracking.audit.support;

import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real PostgreSQL 16 for the audit-log API. The module's surefire configuration excludes the data autoconfiguration
 * for the other tests, so it is switched back on here for these tests only.
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude=")
@AutoConfigureMockMvc
@Import(AuditTestConfiguration.class)
public abstract class AbstractAuditDatabaseTest {
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final String INTERNAL_KEY = "audit-tests-" + UUID.randomUUID() + "-0123456789";
    static { POSTGRES.start(); }

    @Autowired protected JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Set the Spring keys directly so the test does not depend on the ${DB_URL} placeholder of application.yml.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", () -> "127.0.0.1");
        registry.add("spring.rabbitmq.port", () -> "1");
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USER", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("INTERNAL_API_KEY", () -> INTERNAL_KEY);
        // An unavailable broker must not prevent the HTTP API from starting.
        registry.add("RABBITMQ_HOST", () -> "127.0.0.1");
        registry.add("RABBITMQ_PORT", () -> "1");
        registry.add("RABBITMQ_USER", () -> "test");
        registry.add("RABBITMQ_PASSWORD", () -> "test-only");
    }

    /** Inserts an audit row directly (the consumer of #9 owns the real write path). */
    protected void insertAudit(String action, UUID actorUserId, String actorRole, Instant occurredAt,
                               String detailsJson) {
        jdbc.update("INSERT INTO audit_logs (event_id, service, actor_user_id, actor_role, action, entity_type, "
                        + "entity_id, details, occurred_at) VALUES (?, 'pricing-service', ?, ?, ?, 'ZONE', 'z-1', "
                        + "?::jsonb, ?)",
                UUID.randomUUID(), actorUserId, actorRole, action, detailsJson,
                java.sql.Timestamp.from(occurredAt));
    }
}

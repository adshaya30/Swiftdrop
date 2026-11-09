package com.swiftdrop.common.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.error.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Tag("NFR-SEC-019")
class AuditEventsTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    private final InMemoryOutboxEventRepository repository = new InMemoryOutboxEventRepository();
    private final ObjectMapper mapper = EventsAutoConfiguration.defaultMapper();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AuditEvents audit =
            new AuditEvents(new OutboxService(repository, mapper, clock), "pricing-service", clock);

    @BeforeEach
    void inTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void cleanUp() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("FR-PRC-008: record takes the actor from the authenticated caller and writes audit.recorded")
    void actorFromCurrentUser() throws Exception {
        UUID admin = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(admin.toString())
                .claim("role", "ADMIN").claim("typ", "user").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        audit.record("PRICE_CHANGED", "ZONE_PRICING", "WESTERN:CENTRAL",
                Map.of("baseFee", "500.00", "zoneName", "Western"));

        JsonNode data = onlyEventData();
        assertThat(data.get("service").asText()).isEqualTo("pricing-service");
        assertThat(data.get("actorUserId").asText()).isEqualTo(admin.toString());
        assertThat(data.get("actorRole").asText()).isEqualTo("ADMIN");
        assertThat(data.get("action").asText()).isEqualTo("PRICE_CHANGED");
        assertThat(data.get("entityType").asText()).isEqualTo("ZONE_PRICING");
        assertThat(data.get("entityId").asText()).isEqualTo("WESTERN:CENTRAL");
        assertThat(data.get("details").get("baseFee").asText()).isEqualTo("500.00");
        assertThat(data.get("occurredAt").asText()).isEqualTo("2026-11-07T10:00:00Z");
        assertThat(repository.rows.get(0).routingKey()).isEqualTo("audit.recorded");
    }

    @Test
    @DisplayName("SRS 3.5: the system overload records an explicit actor; actorUserId may be null for SYSTEM")
    void systemActor() throws Exception {
        audit.record(null, ActorRole.SYSTEM, "COURIER_UNASSIGNED", "DELIVERY", UUID.randomUUID().toString(), null);

        JsonNode data = onlyEventData();
        assertThat(data.get("actorUserId").isNull()).isTrue();
        assertThat(data.get("actorRole").asText()).isEqualTo("SYSTEM");
        assertThat(data.get("details").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("SRS 3.5: without an authenticated caller the user overload is refused")
    void requiresCaller() {
        assertThatThrownBy(() -> audit.record("X", "Y", "Z", Map.of())).isInstanceOf(DomainException.class);
        assertThat(repository.rows).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "newPassword", "clientSecret", "secret", "refresh_token", "accessToken",
            "email", "contactEmail", "phone", "phoneNumber", "address", "addressLine1", "name", "fullName",
            "contactName", "recipientName", "firstName", "username", "credentials"})
    @DisplayName("BR-28: detail keys with passwords, secrets, tokens or contact data are rejected")
    void rejectsForbiddenKeys(String key) {
        assertThatThrownBy(() -> audit.record(null, ActorRole.ADMIN, "USER_CREATED", "USER", "1", Map.of(key, "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(key);
        assertThat(repository.rows).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"zoneName", "districtName", "businessName", "role", "status", "previousStatus",
            "discountPercent", "courierId", "label", "reason"})
    @DisplayName("BR-28: ordinary business keys are allowed")
    void allowsBusinessKeys(String key) {
        audit.record(null, ActorRole.ADMIN, "ACTION", "ENTITY", "1", Map.of(key, "x"));
        assertThat(repository.rows).hasSize(1);
    }

    @Test
    @DisplayName("BR-28: forbidden keys are also found inside nested objects and lists")
    void nestedKeys() {
        Map<String, Object> nested = Map.of("changes", List.of(Map.of("contactPhone", "x")));
        assertThatThrownBy(() -> audit.record(null, ActorRole.ADMIN, "A", "E", "1", nested))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SRS 3.5: action, entityType, entityId, actorRole and the service name are required")
    void requiredParts() {
        assertThatThrownBy(() -> audit.record(null, null, "A", "E", "1", Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> audit.record(null, ActorRole.ADMIN, " ", "E", "1", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        AuditEvents unnamed = new AuditEvents(new OutboxService(repository, mapper, clock), null, clock);
        assertThatThrownBy(() -> unnamed.record(null, ActorRole.ADMIN, "A", "E", "1", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.application.name");
    }

    private JsonNode onlyEventData() throws Exception {
        assertThat(repository.rows).hasSize(1);
        return mapper.readTree(repository.rows.get(0).payload()).get("data");
    }
}

package com.swiftdrop.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.enums.FailureReason;
import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTopology;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.data.AuditRecordedData;
import com.swiftdrop.common.events.data.DeliveryAssignedData;
import com.swiftdrop.common.events.data.ParcelCreatedData;
import com.swiftdrop.common.events.data.ParcelStatusChangedData;
import com.swiftdrop.common.security.JwtPublicKeyProvider;
import com.swiftdrop.common.testsupport.TestTokens;
import com.swiftdrop.tracking.entity.TrackingParcel;
import com.swiftdrop.tracking.repository.TrackingParcelRepository;
import com.swiftdrop.tracking.scheduler.SseHeartbeatScheduler;
import com.swiftdrop.tracking.service.SseEmitterService;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

/**
 * Prompt 2 tracking checks required by the SRS: TST-023 (tracking part), TST-027 and TST-034.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TrackingServiceIntegrationTest.Keys.class)
public class TrackingServiceIntegrationTest {

    private static final TestTokens TOKENS = TestTokens.create();
    private static final String INTERNAL_KEY = "tracking-test-internal-key-0123456789";
    private static final String NOTE = "SIDE-GATE-NOTE-99";
    private static final String PHONE = "0771234567";
    private static final String EMAIL = "ada@example.com";
    private static final Instant AT = Instant.parse("2026-11-12T04:00:00Z");

    @Container
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tracking_test")
            .withUsername("tracking_test")
            .withPassword("tracking_test_password");

    @Container
    static final RabbitMQContainer BROKER = new RabbitMQContainer("rabbitmq:4-management-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TrackingParcelRepository parcels;

    @Autowired
    private SseEmitterService sse;

    @Autowired
    private SseHeartbeatScheduler heartbeats;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
        registry.add("spring.rabbitmq.host", BROKER::getHost);
        registry.add("spring.rabbitmq.port", BROKER::getAmqpPort);
        registry.add("spring.rabbitmq.username", BROKER::getAdminUsername);
        registry.add("spring.rabbitmq.password", BROKER::getAdminPassword);
        registry.add("INTERNAL_API_KEY", () -> INTERNAL_KEY);
        registry.add("swiftdrop.events.retry.initial-delay-ms", () -> "1");
    }

    @BeforeEach
    void reset() {
        rabbitTemplate.execute(channel -> {
            channel.queuePurge(EventTopology.TRACKING_QUEUE);
            channel.queuePurge(EventTopology.dlqName(EventTopology.TRACKING_QUEUE));
            return null;
        });
        jdbc.update("truncate table tracking_events, audit_logs, processed_events, tracking_parcels");
    }

    @Test
    @Tag("TST-027")
    @DisplayName("TST-027: snapshot then status, terminal close, 404, heartbeat and stream lifetime")
    void sseSnapshotStatusTerminalAndNotFound() throws Exception {
        UUID parcelId = UUID.randomUUID();
        String number = "SD-7K3M9QX2T1";
        publish(created(parcelId, number, UUID.randomUUID(), UUID.randomUUID()));
        awaitCount("tracking_parcels", 1);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/api/v1/public/tracking/" + number + "/stream"))
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("cache-control")).hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        assertThat(response.headers().firstValue("x-accel-buffering")).hasValue("no");

        LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>();
        Thread reader = new Thread(() -> readLines(response.body(), lines), "sse-reader");
        reader.setDaemon(true);
        reader.start();

        String snapshot = awaitLine(lines, "event:snapshot");
        awaitLine(lines, number);
        heartbeats.sendHeartbeats();
        String heartbeat = awaitLine(lines, ": heartbeat");
        assertThat(heartbeat).contains(": heartbeat");

        publish(status(parcelId, number, UUID.randomUUID(), 2, ParcelStatus.CREATED, ParcelStatus.PICKED_UP, null, null));
        awaitLine(lines, "event:status");
        awaitLine(lines, "PICKED_UP");

        publish(status(parcelId, number, UUID.randomUUID(), 3, ParcelStatus.PICKED_UP, ParcelStatus.DELIVERED, null, null));
        awaitLine(lines, "DELIVERED");
        assertThat(awaitLine(lines, "<EOF>")).contains("<EOF>");
        assertThat(snapshot).contains("event:snapshot");

        TrackingParcel stored = parcels.findByTrackingNumber(number).orElseThrow();
        var emitter = sse.register(number, stored);
        assertThat(emitter.getTimeout()).isEqualTo(30L * 60 * 1000);

        assertThat(rest.getForEntity("/api/v1/public/tracking/SD-7K3M9QX2T9", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/api/v1/public/tracking/not-a-number", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/api/v1/public/tracking/not-a-number/stream", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Tag("TST-023")
    @DisplayName("TST-023 tracking: rows, duplicate, stale sequence, unknown parcel dead-letter, audit once")
    void eventProjectionIsIdempotentAndStaleSafe() throws Exception {
        UUID parcelId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String number = "SD-7K3M9QX2T2";
        UUID createdId = UUID.randomUUID();
        publish(created(parcelId, number, createdId, customerId));
        publish(created(parcelId, number, createdId, customerId));
        awaitDrained();
        assertThat(count("tracking_events")).isEqualTo(1);
        assertThat(count("tracking_parcels")).isEqualTo(1);
        assertThat(count("processed_events")).isEqualTo(1);

        UUID statusId = UUID.randomUUID();
        publish(status(parcelId, number, statusId, 2, ParcelStatus.CREATED, ParcelStatus.PICKED_UP, null, null));
        awaitStatus("PICKED_UP");
        publish(status(parcelId, number, UUID.randomUUID(), 2, ParcelStatus.CREATED, ParcelStatus.IN_TRANSIT, null, null));
        awaitDrained();
        assertThat(jdbc.queryForObject("select status from tracking_parcels where parcel_id = ?", String.class, parcelId))
                .isEqualTo("PICKED_UP");
        assertThat(count("tracking_events")).isEqualTo(2);

        UUID unknown = UUID.randomUUID();
        publish(status(unknown, "SD-7K3M9QX2T8", UUID.randomUUID(), 2, ParcelStatus.CREATED, ParcelStatus.PICKED_UP, null, null));
        Message dead = rabbitTemplate.receive(EventTopology.dlqName(EventTopology.TRACKING_QUEUE), 8000);
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).contains(unknown.toString());

        UUID auditId = UUID.randomUUID();
        publish(audit(auditId));
        publish(audit(auditId));
        awaitCount("audit_logs", 1);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where event_id = ?", Long.class, auditId)).isEqualTo(1);

        assertThatThrownBy(() -> jdbc.update("update tracking_events set note = 'changed'"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from tracking_events"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @Tag("TST-034")
    @DisplayName("TST-034 tracking: public JSON is limited to the allowed keys and logs omit the staff note")
    void publicPayloadAndLogsStayPrivate() throws Exception {
        UUID parcelId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String number = "SD-7K3M9QX2T3";
        Logger logger = (Logger) LoggerFactory.getLogger("com.swiftdrop.tracking");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            publish(created(parcelId, number, UUID.randomUUID(), customerId));
            awaitCount("tracking_parcels", 1);
            publish(status(parcelId, number, UUID.randomUUID(), 2, ParcelStatus.CREATED, ParcelStatus.FAILED,
                    FailureReason.RECIPIENT_UNAVAILABLE, NOTE + " " + PHONE + " " + EMAIL));
            awaitStatus("FAILED");
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain(NOTE, PHONE, EMAIL);
        });

        ResponseEntity<String> body = rest.getForEntity("/api/v1/public/tracking/" + number, String.class);
        assertThat(body.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body.getHeaders().getCacheControl()).contains("no-store");
        JsonNode json = objectMapper.readTree(body.getBody());
        assertThat(names(json)).containsExactlyInAnyOrder(
                "trackingNumber", "status", "createdAt", "updatedAt", "origin", "destination", "history");
        assertThat(names(json.get("origin"))).containsExactlyInAnyOrder("districtName", "zoneName");
        assertThat(names(json.get("destination"))).containsExactlyInAnyOrder("districtName", "zoneName");
        assertThat(json.get("history")).hasSize(2);
        assertThat(names(json.get("history").get(1))).containsExactlyInAnyOrder("status", "at", "failureReasonLabel");
        assertThat(json.get("history").get(1).get("failureReasonLabel").asText()).isEqualTo("Recipient not available");
        assertThat(body.getBody()).doesNotContain(NOTE, PHONE, EMAIL, "courier", "customerId", "merchantId", "fee");

        ResponseEntity<String> missing = rest.getForEntity("/api/v1/public/tracking/SD-7K3M9QX2T7", String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).doesNotContain("stackTrace", "Exception", NOTE);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, TOKENS.customer().userId(customerId).bearer());
        ResponseEntity<String> timeline = rest.exchange(
                "/api/v1/tracking/parcels/" + parcelId + "/timeline",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                String.class);
        assertThat(timeline.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(timeline.getBody()).contains(NOTE);

        assertThat(rest.getForEntity("/api/v1/tracking/parcels/" + parcelId + "/timeline", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        HttpHeaders other = new HttpHeaders();
        other.set(HttpHeaders.AUTHORIZATION, TOKENS.customer().bearer());
        assertThat(rest.exchange("/api/v1/tracking/parcels/" + parcelId + "/timeline",
                org.springframework.http.HttpMethod.GET, new org.springframework.http.HttpEntity<>(other), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        HttpHeaders client = new HttpHeaders();
        client.set(HttpHeaders.AUTHORIZATION, TOKENS.client(UUID.randomUUID(), "orders:write").bearer());
        assertThat(rest.exchange("/api/v1/tracking/parcels/" + parcelId + "/timeline",
                org.springframework.http.HttpMethod.GET, new org.springframework.http.HttpEntity<>(client), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        UUID courierId = UUID.randomUUID();
        publish(new EventEnvelope<>(UUID.randomUUID(), EventTypes.DELIVERY_ASSIGNED, EventEnvelope.SCHEMA_VERSION, AT,
                UUID.randomUUID(), new DeliveryAssignedData(parcelId, courierId, "Kamal", AT)));
        awaitCourier(parcelId);

        HttpHeaders admin = new HttpHeaders();
        admin.set(HttpHeaders.AUTHORIZATION, TOKENS.admin().bearer());
        JsonNode adminBody = objectMapper.readTree(rest.exchange(
                "/api/v1/tracking/parcels/" + parcelId + "/timeline",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(admin), String.class).getBody());
        assertThat(adminBody.get("courierId").asText()).isEqualTo(courierId.toString());
        assertThat(adminBody.get("courierFirstName").asText()).isEqualTo("Kamal");

        HttpHeaders courier = new HttpHeaders();
        courier.set(HttpHeaders.AUTHORIZATION, TOKENS.courier().userId(courierId).bearer());
        JsonNode courierBody = objectMapper.readTree(rest.exchange(
                "/api/v1/tracking/parcels/" + parcelId + "/timeline",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(courier), String.class).getBody());
        assertThat(courierBody.get("courierId").isNull()).isTrue();
        assertThat(courierBody.get("courierFirstName").isNull()).isTrue();
    }

    private EventEnvelope<ParcelCreatedData> created(UUID parcelId, String number, UUID eventId, UUID customerId) {
        return new EventEnvelope<>(eventId, EventTypes.PARCEL_CREATED, EventEnvelope.SCHEMA_VERSION, AT,
                UUID.randomUUID(), new ParcelCreatedData(parcelId, number, ParcelStatus.CREATED, OwnerType.CUSTOMER,
                customerId, null, null, "COLOMBO", "Colombo", "Zone A", "KANDY", "Kandy", "Zone B",
                "Colombo", "Kandy", AT));
    }

    private EventEnvelope<ParcelStatusChangedData> status(UUID parcelId, String number, UUID eventId, int sequence,
                                                          ParcelStatus previous, ParcelStatus next,
                                                          FailureReason reason, String note) {
        return new EventEnvelope<>(eventId, EventTypes.PARCEL_STATUS_CHANGED, EventEnvelope.SCHEMA_VERSION, AT,
                UUID.randomUUID(), new ParcelStatusChangedData(parcelId, number, sequence, previous, next, AT,
                null, ActorRole.SYSTEM, note, reason, OwnerType.CUSTOMER, UUID.randomUUID(), null, null));
    }

    private EventEnvelope<AuditRecordedData> audit(UUID eventId) {
        return new EventEnvelope<>(eventId, EventTypes.AUDIT_RECORDED, EventEnvelope.SCHEMA_VERSION, AT,
                UUID.randomUUID(), new AuditRecordedData("tracking-service", null, ActorRole.SYSTEM,
                "PARCEL_VIEWED", "parcel", "parcel-1", Map.of("ok", true), AT));
    }

    private void publish(EventEnvelope<?> event) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        rabbitTemplate.send(EventTopology.EXCHANGE, event.eventType(),
                new Message(objectMapper.writeValueAsBytes(event), properties));
    }

    private void awaitCount(String table, long expected) {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> count(table) == expected);
    }

    private void awaitDrained() {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                rabbitTemplate.execute(channel -> {
                    try {
                        return channel.messageCount(EventTopology.TRACKING_QUEUE) == 0;
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                }));
    }

    private void awaitStatus(String status) {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbc.queryForObject("select count(*) from tracking_parcels where status = ?", Long.class, status) == 1);
    }

    private void awaitCourier(UUID parcelId) {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbc.queryForObject("select courier_first_name from tracking_parcels where parcel_id = ?",
                        String.class, parcelId) != null);
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private static void readLines(InputStream stream, LinkedBlockingQueue<String> lines) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            lines.add("<EOF>");
        } catch (Exception exception) {
            lines.add("<ERR>");
        }
    }

    private static String awaitLine(LinkedBlockingQueue<String> lines, String needle) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        List<String> seen = new ArrayList<>();
        while (System.nanoTime() < deadline) {
            String line = lines.poll(200, TimeUnit.MILLISECONDS);
            if (line == null) {
                continue;
            }
            seen.add(line);
            if (line.contains(needle)) {
                return String.join("\n", seen);
            }
        }
        throw new AssertionError("Timed out waiting for " + needle + " in " + seen);
    }

    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @TestConfiguration
    static class Keys {
        @Bean
        @Primary
        JwtPublicKeyProvider testJwtPublicKeyProvider() {
            return TOKENS.publicKeyProvider();
        }
    }
}

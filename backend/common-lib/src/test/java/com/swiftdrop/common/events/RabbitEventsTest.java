package com.swiftdrop.common.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.rabbitmq.client.GetResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionOperations;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Topology, routing, dead-lettering and the outbox publisher against a real RabbitMQ 4 broker. */
@Testcontainers
@Tag("SRS-3.5")
class RabbitEventsTest {

    @Container
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management-alpine"));

    private static final List<String> ALL_QUEUES = List.of("tracking.events", "delivery.events",
            "notification.events", "tracking.events.dlq", "delivery.events.dlq", "notification.events.dlq");

    static CachingConnectionFactory connectionFactory;
    static RabbitTemplate template;
    static RabbitAdmin admin;

    private final InMemoryOutboxEventRepository repository = new InMemoryOutboxEventRepository();

    @BeforeAll
    static void declareTopology() {
        connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);
        admin = new RabbitAdmin(connectionFactory);
        var declarables = EventTopology.declarables();
        declarables.getDeclarablesByType(Exchange.class).forEach(admin::declareExchange);
        declarables.getDeclarablesByType(Queue.class).forEach(admin::declareQueue);
        declarables.getDeclarablesByType(Binding.class).forEach(admin::declareBinding);
        template = new RabbitTemplate(connectionFactory);
    }

    @AfterAll
    static void close() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void purge() {
        ALL_QUEUES.forEach(q -> admin.purgeQueue(q, false));
    }

    @Test
    @DisplayName("SRS 3.5: each routing key reaches exactly the queues bound to it")
    void routing() {
        Map<String, List<String>> expected = Map.of(
                "parcel.created", List.of("tracking.events", "delivery.events", "notification.events"),
                "parcel.status.changed", List.of("tracking.events", "delivery.events", "notification.events"),
                "delivery.assigned", List.of("tracking.events"),
                "delivery.unassigned", List.of("tracking.events"),
                "audit.recorded", List.of("tracking.events"),
                "courier.changed", List.of("delivery.events"),
                "merchant.status.changed", List.of("notification.events"));
        for (var entry : expected.entrySet()) {
            purge();
            template.convertAndSend(EventTopology.EXCHANGE, entry.getKey(), "{}");
            for (String queue : List.of("tracking.events", "delivery.events", "notification.events")) {
                int count = entry.getValue().contains(queue) ? 1 : 0;
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                        assertThat(messageCount(queue)).as(entry.getKey() + " -> " + queue).isEqualTo(count));
            }
        }
    }

    @Test
    @DisplayName("SRS 3.5: a message rejected without requeue moves to <queue>.dlq")
    void deadLettering() {
        template.convertAndSend(EventTopology.EXCHANGE, EventTypes.AUDIT_RECORDED, "{\"x\":1}");
        await().atMost(Duration.ofSeconds(5)).until(() -> messageCount("tracking.events") == 1);

        template.execute(channel -> {
            GetResponse response = channel.basicGet("tracking.events", false);
            channel.basicReject(response.getEnvelope().getDeliveryTag(), false);
            return null;
        });

        await().atMost(Duration.ofSeconds(5)).until(() -> messageCount("tracking.events.dlq") == 1);
        assertThat(messageCount("tracking.events")).isZero();
    }

    @Test
    @DisplayName("SRS 3.5: the broker holds the queue arguments, so a declaration with other arguments is refused")
    void argumentsAreOnTheBroker() {
        assertThatThrownBy(() -> template.execute(channel ->
                channel.queueDeclare("delivery.events", true, false, false, Map.of())))
                .hasStackTraceContaining("PRECONDITION_FAILED");
    }

    @Test
    @DisplayName("SRS 3.5: the publisher sends rows oldest first as persistent JSON with the AMQP properties, then marks them")
    void publishesInOrder() {
        Instant t = Instant.parse("2026-11-07T10:00:00Z");
        OutboxEvent first = row(EventTypes.COURIER_CHANGED, t);
        OutboxEvent second = row(EventTypes.COURIER_CHANGED, t.plusMillis(1));
        OutboxEvent third = row(EventTypes.COURIER_CHANGED, t.plusMillis(2));
        repository.insert(third);
        repository.insert(first);
        repository.insert(second);

        int published = publisher(connectionFactory).publishBatch();

        assertThat(published).isEqualTo(3);
        for (OutboxEvent expected : List.of(first, second, third)) {
            Message message = template.receive("delivery.events", 5000);
            assertThat(message).isNotNull();
            var props = message.getMessageProperties();
            assertThat(props.getMessageId()).isEqualTo(expected.id().toString());
            assertThat(props.getType()).isEqualTo("courier.changed");
            assertThat(props.getCorrelationId()).isEqualTo(expected.correlationId().toString());
            assertThat(props.getContentType()).isEqualTo("application/json");
            assertThat(props.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(expected.payload());
            assertThat(repository.get(expected.id()).publishedAt()).isNotNull();
        }
    }

    @Test
    @DisplayName("SRS 3.5: an unroutable message stays unpublished, its attempts grow and the batch stops")
    void unroutableStaysUnpublished() {
        Instant t = Instant.parse("2026-11-07T10:00:00Z");
        OutboxEvent unroutable = row("test.unbound", t);
        OutboxEvent next = row(EventTypes.COURIER_CHANGED, t.plusMillis(1));
        repository.insert(unroutable);
        repository.insert(next);

        int published = publisher(connectionFactory).publishBatch();

        assertThat(published).isZero();
        assertThat(repository.get(unroutable.id()).publishedAt()).isNull();
        assertThat(repository.get(unroutable.id()).attempts()).isEqualTo(1);
        assertThat(repository.get(next.id()).attempts()).isZero();
        assertThat(repository.get(next.id()).publishedAt()).isNull();
        assertThat(messageCount("delivery.events")).isZero();
    }

    @Test
    @DisplayName("NFR-REL-001: with the broker unreachable nothing is lost: rows stay unpublished and nothing throws")
    void brokerDown() throws IOException {
        CachingConnectionFactory down = new CachingConnectionFactory("localhost", freePort());
        down.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        down.setPublisherReturns(true);
        try {
            OutboxEvent event = row(EventTypes.COURIER_CHANGED, Instant.now());
            repository.insert(event);
            OutboxPublisher publisher = publisher(down);

            assertThatCode(publisher::tick).doesNotThrowAnyException();

            assertThat(repository.get(event.id()).publishedAt()).isNull();
            assertThat(repository.get(event.id()).attempts()).isEqualTo(1);
        } finally {
            down.destroy();
        }
    }

    @Test
    @DisplayName("SRS 6.1: in a service without an outbox_events table the publisher stays idle")
    void idleWithoutTable() {
        repository.tableExists = false;
        OutboxPublisher publisher = publisher(connectionFactory);

        publisher.tick();

        assertThat(publisher.state()).isEqualTo(OutboxPublisher.State.IDLE);
    }

    @Test
    @DisplayName("NFR-REL-003: a database error is logged and retried on the next run")
    void databaseDownIsRetried() {
        repository.failure = new IllegalStateException("database down");
        OutboxPublisher publisher = publisher(connectionFactory);

        assertThatCode(publisher::tick).doesNotThrowAnyException();
        assertThat(publisher.state()).isEqualTo(OutboxPublisher.State.UNKNOWN);

        repository.failure = null;
        publisher.tick();
        assertThat(publisher.state()).isEqualTo(OutboxPublisher.State.ACTIVE);
    }

    @Test
    @DisplayName("(S) SRS 3.5: published rows older than one day are deleted at most once a day; backlog gauge")
    void cleanupAndBacklog() {
        Instant now = Instant.parse("2026-11-07T10:00:00Z");
        OutboxEvent old = published(row(EventTypes.COURIER_CHANGED, now.minus(Duration.ofDays(3))),
                now.minus(Duration.ofDays(2)));
        OutboxEvent recent = published(row(EventTypes.COURIER_CHANGED, now.minus(Duration.ofHours(2))),
                now.minus(Duration.ofHours(1)));
        repository.insert(old);
        repository.insert(recent);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxPublisher publisher = new OutboxPublisher(repository, connectionFactory,
                TransactionOperations.withoutTransaction(), 1000, Clock.fixed(now, ZoneOffset.UTC), registry);

        publisher.tick();

        assertThat(repository.rows).extracting(OutboxEvent::id).containsExactly(recent.id());
        repository.insert(published(row(EventTypes.COURIER_CHANGED, now.minus(Duration.ofDays(4))),
                now.minus(Duration.ofDays(3))));
        repository.insert(row("test.unbound", now));
        publisher.tick();
        assertThat(repository.rows).hasSize(3);
        assertThat(publisher.backlog()).isEqualTo(1);
        assertThat(registry.get(OutboxPublisher.BACKLOG_METRIC).gauge().value()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("SRS 3.5: once started, the publisher sends new rows every poll interval and stops cleanly")
    void lifecycle() {
        OutboxPublisher publisher = new OutboxPublisher(repository, connectionFactory,
                TransactionOperations.withoutTransaction(), 50, Clock.systemUTC(), null);
        publisher.start();
        try {
            assertThat(publisher.isRunning()).isTrue();
            OutboxEvent event = row(EventTypes.MERCHANT_STATUS_CHANGED, Instant.now());
            repository.insert(event);
            await().atMost(Duration.ofSeconds(10)).until(() -> repository.get(event.id()).publishedAt() != null);
            assertThat(template.receive("notification.events", 5000)).isNotNull();
        } finally {
            publisher.stop();
        }
        assertThat(publisher.isRunning()).isFalse();
        assertThatThrownBy(() -> new OutboxPublisher(repository, connectionFactory,
                TransactionOperations.withoutTransaction(), 0, Clock.systemUTC(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private OutboxPublisher publisher(CachingConnectionFactory factory) {
        return new OutboxPublisher(repository, factory, TransactionOperations.withoutTransaction(), 1000,
                Clock.systemUTC(), null);
    }

    private static OutboxEvent row(String routingKey, Instant createdAt) {
        UUID id = UUID.randomUUID();
        return new OutboxEvent(id, routingKey, routingKey, "{\"eventId\":\"" + id + "\"}", UUID.randomUUID(),
                createdAt, null, 0);
    }

    private static OutboxEvent published(OutboxEvent r, Instant at) {
        return new OutboxEvent(r.id(), r.eventType(), r.routingKey(), r.payload(), r.correlationId(), r.createdAt(),
                at, r.attempts());
    }

    private static int messageCount(String queue) {
        return template.execute(channel -> (int) channel.messageCount(queue));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}

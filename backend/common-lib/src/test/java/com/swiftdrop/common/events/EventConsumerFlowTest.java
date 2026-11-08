package com.swiftdrop.common.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.events.data.AuditRecordedData;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** A service with only the dependency and a @RabbitListener, consuming from a real RabbitMQ 4 broker. */
@Testcontainers
@Tag("SRS-3.5")
class EventConsumerFlowTest {

    @Container
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management-alpine"));

    private static final String QUEUE = EventTopology.TRACKING_QUEUE;
    private static final String DLQ = EventTopology.dlqName(QUEUE);

    private static CachingConnectionFactory connectionFactory;
    private static RabbitTemplate template;
    private static RabbitAdmin admin;

    private final ObjectMapper mapper = EventsAutoConfiguration.defaultMapper();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, RabbitAutoConfiguration.class,
                    EventsAutoConfiguration.class))
            .withUserConfiguration(ListenerConfiguration.class)
            .withPropertyValues(EventsAutoConfiguration.RETRY_DELAY_PROPERTY + "=1");

    @BeforeAll
    static void declareTopology() {
        connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());
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
        List.of(QUEUE, DLQ).forEach(q -> admin.purgeQueue(q, false));
    }

    private ApplicationContextRunner connected() {
        return runner.withPropertyValues("spring.rabbitmq.host=" + RABBIT.getHost(),
                "spring.rabbitmq.port=" + RABBIT.getAmqpPort(),
                "spring.rabbitmq.username=" + RABBIT.getAdminUsername(),
                "spring.rabbitmq.password=" + RABBIT.getAdminPassword());
    }

    @Test
    @DisplayName("SRS 3.5: every @RabbitListener runs with manual ack, prefetch 10 and non-fatal missing queues")
    void listenerDefaults() {
        connected().run(ctx -> {
            assertThat(ctx).hasNotFailed();
            var containers = ctx.getBean(RabbitListenerEndpointRegistry.class).getListenerContainers();
            assertThat(containers).hasSize(1);
            AbstractMessageListenerContainer container =
                    (AbstractMessageListenerContainer) containers.iterator().next();
            assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
            assertThat(ReflectionTestUtils.getField(container, "prefetchCount")).isEqualTo(10);
            assertThat(ReflectionTestUtils.getField(container, "missingQueuesFatal")).isEqualTo(false);
        });
    }

    @Test
    @DisplayName("SRS 3.5: a handled message is acknowledged and leaves the queue")
    void handledIsAcked() throws Exception {
        String body = audit(UUID.randomUUID());

        connected().run(ctx -> {
            TestListener listener = ctx.getBean(TestListener.class);
            publish(body);
            await().atMost(Duration.ofSeconds(20)).until(() -> listener.attempts.get() == 1);
        });

        // An unacknowledged message would return to the queue when the listener's channel closes.
        assertStaysEmpty(QUEUE);
        assertThat(messageCount(DLQ)).isZero();
    }

    @Test
    @Tag("TST-026")
    @DisplayName("TST-026: an always-failing message ends in the DLQ after exactly 3 attempts")
    void alwaysFailingGoesToDlq() throws Exception {
        String body = audit(UUID.randomUUID());

        connected().run(ctx -> {
            TestListener listener = ctx.getBean(TestListener.class);
            listener.behaviour = e -> {
                throw new IllegalStateException("always");
            };
            publish(body);
            await().atMost(Duration.ofSeconds(20)).until(() -> messageCount(DLQ) == 1);
            await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(5))
                    .until(() -> listener.attempts.get() == 3);
        });

        assertThat(messageCount(QUEUE)).isZero();
        Message dead = template.receive(DLQ, 5000);
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo(body);
    }

    @Test
    @DisplayName("SRS 3.5: a not-yet-processable event is retried and acknowledged once it succeeds")
    void retryableThenSuccess() throws Exception {
        String body = audit(UUID.randomUUID());

        connected().run(ctx -> {
            TestListener listener = ctx.getBean(TestListener.class);
            listener.behaviour = e -> {
                if (listener.attempts.get() == 1) {
                    throw new RetryableEventException("parcel not known yet");
                }
            };
            publish(body);
            await().atMost(Duration.ofSeconds(20)).until(() -> listener.attempts.get() == 2);
        });

        assertStaysEmpty(QUEUE);
        assertThat(messageCount(DLQ)).isZero();
    }

    @Test
    @Tag("TST-026")
    @DisplayName("TST-026: redelivery of the same eventId is acknowledged and processed once")
    void redeliveryProcessedOnce() throws Exception {
        ProcessedEvents processed = new ProcessedEvents(new SeenEventsJdbcTemplate(), Clock.systemUTC());
        AtomicInteger businessEffects = new AtomicInteger();
        String body = audit(UUID.randomUUID());

        connected().run(ctx -> {
            TestListener listener = ctx.getBean(TestListener.class);
            listener.behaviour = e -> {
                TransactionSynchronizationManager.setActualTransactionActive(true);
                try {
                    if (processed.firstTime(e.eventId())) {
                        businessEffects.incrementAndGet();
                    }
                } finally {
                    TransactionSynchronizationManager.setActualTransactionActive(false);
                }
            };
            publish(body);
            publish(body);
            await().atMost(Duration.ofSeconds(20)).until(() -> listener.attempts.get() == 2);
        });

        assertThat(businessEffects).hasValue(1);
        assertStaysEmpty(QUEUE);
        assertThat(messageCount(DLQ)).isZero();
    }

    @Test
    @DisplayName("SRS 3.5: a message that is not an event goes to the DLQ without reaching the handler")
    void poisonMessage() {
        connected().run(ctx -> {
            TestListener listener = ctx.getBean(TestListener.class);
            template.convertAndSend(EventTopology.EXCHANGE, EventTypes.AUDIT_RECORDED, "not an event");
            await().atMost(Duration.ofSeconds(20)).until(() -> messageCount(DLQ) == 1);
            assertThat(listener.attempts).hasValue(0);
        });
    }

    @Test
    @DisplayName("NFR-REL-003: a service with a listener starts even when the broker is unreachable")
    void startsWithoutBroker() throws IOException {
        runner.withPropertyValues("spring.rabbitmq.host=localhost", "spring.rabbitmq.port=" + freePort())
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(EventConsumerSupport.class);
                });
    }

    private void publish(String body) {
        template.send(EventTopology.EXCHANGE, EventTypes.AUDIT_RECORDED,
                new Message(body.getBytes(StandardCharsets.UTF_8)));
    }

    private String audit(UUID correlationId) throws Exception {
        AuditRecordedData data = new AuditRecordedData("test-service", null, ActorRole.SYSTEM, "ACTION", "ENTITY", "1",
                Map.of(), Instant.parse("2026-11-08T10:00:00Z"));
        return mapper.writeValueAsString(
                EventEnvelope.create(EventTypes.AUDIT_RECORDED, Instant.now(), correlationId, data));
    }

    private static void assertStaysEmpty(String queue) {
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).until(() -> messageCount(queue) == 0);
    }

    private static int messageCount(String queue) {
        return template.execute(channel -> (int) channel.messageCount(queue));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ListenerConfiguration {

        @Bean
        TestListener testListener(EventConsumerSupport consumer) {
            return new TestListener(consumer);
        }
    }

    static class TestListener {

        final AtomicInteger attempts = new AtomicInteger();
        volatile EventConsumerSupport.EventHandler behaviour = e -> { };
        private final EventConsumerSupport consumer;

        TestListener(EventConsumerSupport consumer) {
            this.consumer = consumer;
        }

        @RabbitListener(queues = EventTopology.TRACKING_QUEUE)
        public void onMessage(Message message, Channel channel) throws IOException {
            consumer.handle(message, channel, envelope -> {
                attempts.incrementAndGet();
                behaviour.handle(envelope);
            });
        }
    }
}

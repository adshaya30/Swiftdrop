package com.swiftdrop.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTopology;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.data.ParcelCreatedData;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.repository.NotificationRepository;
import com.swiftdrop.notification.service.EmailSender;
import com.swiftdrop.notification.service.NotificationContactResolver;
import com.swiftdrop.notification.service.NotificationLockService;
import com.swiftdrop.notification.service.SmsSender;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.awaitility.Awaitility;

@Testcontainers
@SpringBootTest(properties = {
        "swiftdrop.events.retry.initial-delay-ms=10",
        "spring.rabbitmq.listener.simple.concurrency=2",
        "spring.rabbitmq.listener.simple.max-concurrency=2",
        "INTERNAL_API_KEY=test-internal-api-key-12345678901234567890",
        "WEBHOOK_SECRET_ENCRYPTION_KEY=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "logging.level.root=WARN"
})
class NotificationServiceIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4-management-alpine"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired NotificationRepository repository;
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired RabbitAdmin rabbitAdmin;
    @Autowired ObjectMapper mapper;
    @Autowired NotificationLockService locks;

    @MockitoBean NotificationContactResolver contacts;
    @MockitoBean EmailSender emailSender;
    @MockitoBean SmsSender smsSender;
    @MockitoBean JwtDecoder jwtDecoder;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        rabbitAdmin.purgeQueue(EventTopology.NOTIFICATION_QUEUE, false);
        rabbitAdmin.purgeQueue(EventTopology.dlqName(EventTopology.NOTIFICATION_QUEUE), false);
        clearInvocations(contacts, emailSender, smsSender);
        when(contacts.resolve(any(), any())).thenReturn(new NotificationContactResolver.Contact(
                "sender@example.test", "+94771234567", "Private Sender Name", null));
    }

    @Test
    void successfulDeliveryPersistsSmsOnlyAndAcknowledgesOriginalEvent() throws Exception {
        EventEnvelope<?> event = customerParcelCreated();
        publish(event);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(rows(event.eventId())).hasSize(2).allMatch(row -> "SENT".equals(row.getStatus()));
            assertThat(queueCount(EventTopology.NOTIFICATION_QUEUE)).isZero();
        });
        verify(emailSender, times(1)).send(org.mockito.ArgumentMatchers.eq("sender@example.test"),
                org.mockito.ArgumentMatchers.eq("SwiftDrop: Parcel Created"),
                org.mockito.ArgumentMatchers.contains("/track/SD1234567890"));
        verify(smsSender, times(1)).send(org.mockito.ArgumentMatchers.eq("+94771234567"),
                org.mockito.ArgumentMatchers.argThat(body -> body.length() <= 160
                        && body.contains("SD1234567890") && body.contains("/track/SD1234567890")
                        && !body.contains("Private Sender Name") && !body.contains("sender@example.test")
                        && !body.contains("+94771234567")));
        List<Notification> rows = rows(event.eventId());
        assertThat(rows).filteredOn(row -> "EMAIL".equals(row.getChannel())).allMatch(row -> row.getBodyPreview() == null);
        assertThat(rows).filteredOn(row -> "SMS".equals(row.getChannel())).allMatch(row ->
                row.getBodyPreview() != null && row.getBodyPreview().length() <= 160
                        && !row.getBodyPreview().contains("Private Sender Name"));
        assertThat(rows).allMatch(row -> "s***@example.test".equals(row.getDestinationMasked())
                || "+94*****4567".equals(row.getDestinationMasked()));
    }

    @Test
    void redeliveryDoesNotResendSentRows() throws Exception {
        EventEnvelope<?> event = customerParcelCreated();
        publish(event);
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(rows(event.eventId())).hasSize(2).allMatch(row -> "SENT".equals(row.getStatus())));
        publish(event);
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(queueCount(EventTopology.NOTIFICATION_QUEUE)).isZero());
        verify(emailSender, times(1)).send(any(), any(), any());
        verify(smsSender, times(1)).send(any(), any());
    }

    @Test
    void concurrentDuplicateDeliveriesAreSerializedAcrossWorkers() throws Exception {
        doAnswer(invocation -> {
            Thread.sleep(250);
            return null;
        }).when(emailSender).send(any(), any(), any());
        EventEnvelope<?> event = customerParcelCreated();
        publish(event);
        publish(event);

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(queueCount(EventTopology.NOTIFICATION_QUEUE)).isZero();
            assertThat(rows(event.eventId())).hasSize(2).allMatch(row -> "SENT".equals(row.getStatus()));
        });
        assertThat(queueCount(EventTopology.dlqName(EventTopology.NOTIFICATION_QUEUE))).isZero();
        verify(emailSender, times(1)).send(any(), any(), any());
        verify(smsSender, times(1)).send(any(), any());
    }

    @Test
    void missingDestinationsAreSkippedAndAcknowledged() throws Exception {
        when(contacts.resolve(any(), any())).thenReturn(NotificationContactResolver.Contact.empty());
        EventEnvelope<?> event = customerParcelCreated();
        publish(event);
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(rows(event.eventId())).hasSize(2).allMatch(row -> "SKIPPED".equals(row.getStatus()));
            assertThat(queueCount(EventTopology.NOTIFICATION_QUEUE)).isZero();
        });
        verify(emailSender, never()).send(any(), any(), any());
        verify(smsSender, never()).send(any(), any());
    }

    @Test
    void transientDeliveryFailureRetriesAndAcknowledgesAfterRecovery() throws Exception {
        AtomicInteger emailAttempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (emailAttempts.incrementAndGet() == 1) throw new IllegalStateException("temporary SMTP error");
            return null;
        }).when(emailSender).send(any(), any(), any());
        EventEnvelope<?> event = customerParcelCreated();
        publish(event);

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(rows(event.eventId())).hasSize(2).allMatch(row -> "SENT".equals(row.getStatus()));
            assertThat(queueCount(EventTopology.NOTIFICATION_QUEUE)).isZero();
        });
        assertThat(queueCount(EventTopology.dlqName(EventTopology.NOTIFICATION_QUEUE))).isZero();
        assertThat(rows(event.eventId())).filteredOn(row -> "EMAIL".equals(row.getChannel()))
                .singleElement().satisfies(row -> assertThat(row.getAttempts()).isEqualTo(2));
        verify(emailSender, times(2)).send(any(), any(), any());
        verify(smsSender, times(1)).send(any(), any());
    }

    @Test
    void transientChannelFailureRetriesThenDeadLettersWithoutResendingSuccessfulChannel() throws Exception {
        doThrow(new IllegalStateException("SMTP rejected sender@example.test"))
                .when(emailSender).send(any(), any(), any());
        EventEnvelope<?> event = customerParcelCreated();
        publish(event);

        Awaitility.await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
            assertThat(queueCount(EventTopology.dlqName(EventTopology.NOTIFICATION_QUEUE))).isEqualTo(1);
            List<Notification> rows = rows(event.eventId());
            assertThat(rows).filteredOn(row -> "EMAIL".equals(row.getChannel())).singleElement().satisfies(row -> {
                assertThat(row.getStatus()).isEqualTo("FAILED");
                assertThat(row.getAttempts()).isEqualTo(3);
                assertThat(row.getErrorSummary()).isEqualTo("Notification delivery failed");
                assertThat(row.getErrorSummary()).doesNotContain("sender@example.test");
            });
            assertThat(rows).filteredOn(row -> "SMS".equals(row.getChannel())).singleElement().satisfies(row -> {
                assertThat(row.getStatus()).isEqualTo("SENT");
                assertThat(row.getAttempts()).isEqualTo(1);
            });
        });
        verify(emailSender, times(3)).send(any(), any(), any());
        verify(smsSender, times(1)).send(any(), any());
        Message deadLetter = rabbitTemplate.receive(EventTopology.dlqName(EventTopology.NOTIFICATION_QUEUE), 2000);
        assertThat(deadLetter).isNotNull();
        assertThat(mapper.readTree(deadLetter.getBody()).path("eventId").asText()).isEqualTo(event.eventId().toString());
    }

    @Test
    void postgresAdvisoryLockExcludesASecondWorkerAndReleasesForRetry() throws Exception {
        UUID id = UUID.randomUUID();
        NotificationLockService.Lock first = locks.acquire(id);
        CompletableFuture<NotificationLockService.Lock> waiting =
                CompletableFuture.supplyAsync(() -> locks.acquire(id));
        try {
            assertThat(waiting).isNotCompleted();
        } finally {
            first.close();
        }
        try (NotificationLockService.Lock afterRelease = waiting.get(5, TimeUnit.SECONDS)) {
            assertThat(afterRelease).isNotNull();
        }
    }

    private void publish(EventEnvelope<?> envelope) throws Exception {
        byte[] body = mapper.writeValueAsBytes(envelope);
        rabbitTemplate.send(EventTopology.EXCHANGE, envelope.eventType(), new Message(body, new MessageProperties()));
    }

    private List<Notification> rows(UUID eventId) {
        return repository.findAllByEventId(eventId);
    }

    private Integer queueCount(String queue) {
        return rabbitAdmin.getQueueInfo(queue).getMessageCount();
    }

    private static EventEnvelope<?> customerParcelCreated() {
        ParcelCreatedData data = new ParcelCreatedData(UUID.randomUUID(), "SD1234567890", ParcelStatus.CREATED,
                OwnerType.CUSTOMER, UUID.randomUUID(), null, null, "D1", "Colombo", "WEST", "D2", "Kandy", "CENTRAL",
                "Colombo", "Kandy", Instant.now());
        return EventEnvelope.create(EventTypes.PARCEL_CREATED, Instant.now(), UUID.randomUUID(), data);
    }
}

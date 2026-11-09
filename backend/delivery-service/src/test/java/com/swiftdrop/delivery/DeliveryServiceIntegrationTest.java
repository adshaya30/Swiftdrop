package com.swiftdrop.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.enums.UserStatus;
import com.swiftdrop.common.enums.VehicleType;
import com.swiftdrop.common.events.EventEnvelope;
import com.swiftdrop.common.events.EventTopology;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.data.CourierChangedData;
import com.swiftdrop.common.events.data.ParcelCreatedData;
import com.swiftdrop.common.events.data.ParcelStatusChangedData;
import com.swiftdrop.common.security.JwtPublicKeyProvider;
import com.swiftdrop.common.testsupport.TestTokens;
import com.swiftdrop.delivery.domain.Courier;
import com.swiftdrop.delivery.domain.CourierDeliveryQueries;
import com.swiftdrop.delivery.domain.CourierRepository;
import com.swiftdrop.delivery.domain.Delivery;
import com.swiftdrop.delivery.domain.DeliveryAssignment;
import com.swiftdrop.delivery.domain.DeliveryAssignmentRepository;
import com.swiftdrop.delivery.domain.DeliveryListItem;
import com.swiftdrop.delivery.domain.DeliveryRepository;
import com.swiftdrop.delivery.domain.Scope;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@Import(DeliveryServiceIntegrationTest.TestConfigurationForDelivery.class)
class DeliveryServiceIntegrationTest {

    private static final Instant COLOMBO_DAY_START = Instant.parse("2026-11-07T18:30:00Z");
    private static final Clock TEST_CLOCK = Clock.fixed(COLOMBO_DAY_START, ZoneOffset.UTC);
    private static final TestTokens TOKENS = TestTokens.create();
    private static final String INTERNAL_KEY = "delivery-test-internal-key-0123456789";

    @Container
    private static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    private static final RabbitMQContainer BROKER = new RabbitMQContainer("rabbitmq:4-management-alpine");

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private CourierRepository courierRepository;

    @Autowired
    private DeliveryAssignmentRepository assignmentRepository;

    @Autowired
    private CourierDeliveryQueries courierDeliveryQueries;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Flyway flyway;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void serviceProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", DATABASE::getJdbcUrl);
        registry.add("DB_USER", DATABASE::getUsername);
        registry.add("DB_PASSWORD", DATABASE::getPassword);
        registry.add("RABBITMQ_HOST", BROKER::getHost);
        registry.add("RABBITMQ_PORT", BROKER::getAmqpPort);
        registry.add("RABBITMQ_USER", BROKER::getAdminUsername);
        registry.add("RABBITMQ_PASSWORD", BROKER::getAdminPassword);
        registry.add("INTERNAL_API_KEY", () -> INTERNAL_KEY);
        registry.add("OUTBOX_POLL_INTERVAL_MS", () -> "60000");
        registry.add("swiftdrop.events.retry.initial-delay-ms", () -> "1");
    }

    @BeforeEach
    void clearTables() {
        rabbitTemplate.execute(channel -> {
            channel.queuePurge(EventTopology.DELIVERY_QUEUE);
            channel.queuePurge(EventTopology.dlqName(EventTopology.DELIVERY_QUEUE));
            return null;
        });
        assignmentRepository.deleteAll();
        deliveryRepository.deleteAll();
        courierRepository.deleteAll();
        jdbcTemplate.update("delete from processed_events");
        jdbcTemplate.update("delete from outbox_events");
    }

    @Test
    @Tag("TST-001")
    @DisplayName("TST-001 FR-DEL-001: Flyway creates the required delivery schema and validates the JPA mappings")
    void flywayCreatesDeliverySchema() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        List<String> tableNames = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(tableNames).contains(
                "deliveries", "couriers", "delivery_assignments", "delivery_proofs", "proof_photos",
                "processed_events", "outbox_events");
        assertThat(jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = 'outbox_events'",
                String.class)).containsExactlyInAnyOrder(
                        "id", "event_type", "routing_key", "payload", "correlation_id", "created_at",
                        "published_at", "attempts");
    }

    @Test
    @Tag("TST-030")
    @DisplayName("TST-030 FR-DEL-003 BR-18: scopes use Colombo days and never return another courier's deliveries")
    void courierScopesUseColomboDayAndEnforceCourierId() {
        UUID courierId = UUID.randomUUID();
        UUID anotherCourierId = UUID.randomUUID();
        courierRepository.save(new Courier(courierId, "Courier One", VehicleType.MOTORBIKE, "ABC-1234", UserStatus.ACTIVE));
        courierRepository.save(new Courier(anotherCourierId, "Courier Two", VehicleType.VAN, "XYZ-9876", UserStatus.ACTIVE));

        Delivery created = saveDelivery(courierId, ParcelStatus.CREATED, null, COLOMBO_DAY_START.minus(Duration.ofDays(2)));
        Delivery inTransit = saveDelivery(courierId, ParcelStatus.IN_TRANSIT, null, COLOMBO_DAY_START.minus(Duration.ofDays(1)));
        Delivery deliveredAtMidnight = saveDelivery(courierId, ParcelStatus.DELIVERED, COLOMBO_DAY_START,
                COLOMBO_DAY_START.minus(Duration.ofHours(2)));
        Delivery cancelledToday = saveDelivery(courierId, ParcelStatus.CANCELLED, COLOMBO_DAY_START.plus(Duration.ofHours(12)),
                COLOMBO_DAY_START.minus(Duration.ofHours(1)));
        Delivery terminalBeforeToday = saveDelivery(courierId, ParcelStatus.FAILED,
                COLOMBO_DAY_START.minusSeconds(1), COLOMBO_DAY_START.minus(Duration.ofHours(3)));
        Delivery terminalTomorrow = saveDelivery(courierId, ParcelStatus.DELIVERED,
                COLOMBO_DAY_START.plus(Duration.ofDays(1)), COLOMBO_DAY_START.minus(Duration.ofHours(4)));
        Delivery anotherCouriersDelivery = saveDelivery(anotherCourierId, ParcelStatus.CANCELLED,
                COLOMBO_DAY_START.plus(Duration.ofHours(3)), COLOMBO_DAY_START);

        List<DeliveryListItem> today = courierDeliveryQueries.listForCourier(courierId, Scope.TODAY,
                org.springframework.data.domain.PageRequest.of(0, 20)).getContent();
        List<DeliveryListItem> active = courierDeliveryQueries.listForCourier(courierId, Scope.ACTIVE,
                org.springframework.data.domain.PageRequest.of(0, 20)).getContent();
        List<DeliveryListItem> completed = courierDeliveryQueries.listForCourier(courierId, Scope.COMPLETED,
                org.springframework.data.domain.PageRequest.of(0, 20)).getContent();

        assertThat(today).extracting(DeliveryListItem::parcelId)
                .containsExactly(created.getParcelId(), inTransit.getParcelId(), deliveredAtMidnight.getParcelId(),
                        cancelledToday.getParcelId());
        assertThat(today).extracting(DeliveryListItem::parcelId)
                .doesNotContain(terminalBeforeToday.getParcelId(), terminalTomorrow.getParcelId(),
                        anotherCouriersDelivery.getParcelId());
        assertThat(active).extracting(DeliveryListItem::parcelId)
                .containsExactly(created.getParcelId(), inTransit.getParcelId());
        assertThat(completed).extracting(DeliveryListItem::parcelId)
                .contains(terminalBeforeToday.getParcelId(), deliveredAtMidnight.getParcelId(), cancelledToday.getParcelId(),
                        terminalTomorrow.getParcelId())
                .doesNotContain(anotherCouriersDelivery.getParcelId(), created.getParcelId());
    }

    @Test
    @Tag("TST-022")
    @DisplayName("TST-022 FR-DEL-002 FR-DEL-005: assignment and courier lookup serialize on the same row lock")
    void assignmentAndCourierLookupsSerializeOnTheSameLock() throws Exception {
        UUID courierId = UUID.randomUUID();
        courierRepository.save(new Courier(courierId, "Courier One", VehicleType.MOTORBIKE, "ABC-1234", UserStatus.ACTIVE));
        UUID parcelId = saveDelivery(courierId, ParcelStatus.CREATED, null, COLOMBO_DAY_START).getParcelId();
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        CountDownLatch firstLockHeld = new CountDownLatch(1);
        CountDownLatch secondAttemptStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstLock = new CountDownLatch(1);
        CountDownLatch secondLockAcquired = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> transactions.executeWithoutResult(status -> {
                assertThat(deliveryRepository.findByIdForUpdate(parcelId)).isPresent();
                firstLockHeld.countDown();
                await(releaseFirstLock);
            }));
            assertThat(firstLockHeld.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> {
                secondAttemptStarted.countDown();
                transactions.executeWithoutResult(status -> {
                    assertThat(deliveryRepository.findByParcelIdAndCourierIdForUpdate(parcelId, courierId)).isPresent();
                    secondLockAcquired.countDown();
                });
            });
            assertThat(secondAttemptStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Awaitility.await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2))
                    .until(() -> secondLockAcquired.getCount() == 1);

            releaseFirstLock.countDown();
            assertThat(secondLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirstLock.countDown();
            executor.shutdownNow();
        }
    }

        @Test
        @Tag("TST-025")
        @DisplayName("TST-025 FR-DEL-001: parcel.created creates one unassigned delivery and deduplicates redelivery")
        void parcelCreatedCreatesDeliveryOnce() throws Exception {
        UUID parcelId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-11-08T10:15:30Z");
        EventEnvelope<ParcelCreatedData> envelope = envelope(EventTypes.PARCEL_CREATED, eventId,
            new ParcelCreatedData(parcelId, "SD-7K3M9QX2TA", ParcelStatus.CREATED, OwnerType.MERCHANT,
                null, merchantId, "ORDER-1", "COLOMBO", "Colombo", "Western",
                "KANDY", "Kandy", "Central", "Colombo 03", "Kandy", createdAt));

        publish(envelope);
        publish(envelope);

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Delivery delivery = deliveryRepository.findById(parcelId).orElseThrow();
            assertThat(delivery.getTrackingNumber()).isEqualTo("SD-7K3M9QX2TA");
            assertThat(delivery.getOwnerType()).isEqualTo(OwnerType.MERCHANT);
            assertThat(delivery.getCustomerId()).isNull();
            assertThat(delivery.getMerchantId()).isEqualTo(merchantId);
            assertThat(delivery.getOriginDistrictCode()).isEqualTo("COLOMBO");
            assertThat(delivery.getOriginDistrictName()).isEqualTo("Colombo");
            assertThat(delivery.getDestinationDistrictCode()).isEqualTo("KANDY");
            assertThat(delivery.getDestinationDistrictName()).isEqualTo("Kandy");
            assertThat(delivery.getPickupCity()).isEqualTo("Colombo 03");
            assertThat(delivery.getDropoffCity()).isEqualTo("Kandy");
            assertThat(delivery.getParcelCreatedAt()).isEqualTo(createdAt);
            assertThat(delivery.getStatus()).isEqualTo(ParcelStatus.CREATED);
            assertThat(delivery.getStatusSequence()).isEqualTo(1);
            assertThat(delivery.getCourierId()).isNull();
            assertThat(jdbcTemplate.queryForObject("select count(*) from processed_events where event_id = ?",
                Long.class, eventId)).isEqualTo(1);
        });
        }

        @Test
        @Tag("TST-025")
        @DisplayName("TST-025 FR-DEL-001 SRS 3.5: status updates advance sequence, ignore stale events, and retain assignments")
        void statusUpdatesAreSequenceSafeAndPreserveAssignment() throws Exception {
        UUID parcelId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        courierRepository.save(new Courier(courierId, "Amal Perera", VehicleType.MOTORBIKE, "ABC-1234", UserStatus.ACTIVE));
        publishParcelCreated(parcelId);
        awaitDelivery(parcelId, delivery -> delivery.getStatusSequence() == 1);

        Delivery delivery = deliveryRepository.findById(parcelId).orElseThrow();
        Instant assignedAt = COLOMBO_DAY_START.minusSeconds(30);
        delivery.assignTo(courierId, assignedAt);
        deliveryRepository.save(delivery);
        Instant originalTerminalAt = Instant.parse("2026-11-09T01:02:03Z");
        EventEnvelope<ParcelStatusChangedData> delivered = statusEvent(parcelId, UUID.randomUUID(), 3,
            ParcelStatus.DELIVERED, originalTerminalAt);
        publish(statusEvent(parcelId, UUID.randomUUID(), 2, ParcelStatus.IN_TRANSIT,
            Instant.parse("2026-11-08T10:00:00Z")));
        awaitDelivery(parcelId, item -> item.getStatusSequence() == 2);
        publish(delivered);
        awaitDelivery(parcelId, item -> item.getStatusSequence() == 3 && item.getStatus() == ParcelStatus.DELIVERED);
        publish(statusEvent(parcelId, UUID.randomUUID(), 2, ParcelStatus.FAILED,
            Instant.parse("2026-11-08T11:00:00Z")));
        publish(delivered);

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Delivery current = deliveryRepository.findById(parcelId).orElseThrow();
            assertThat(current.getStatus()).isEqualTo(ParcelStatus.DELIVERED);
            assertThat(current.getStatusSequence()).isEqualTo(3);
            assertThat(current.getTerminalAt()).isEqualTo(originalTerminalAt);
            assertThat(current.getCourierId()).isEqualTo(courierId);
            assertThat(current.getAssignedAt()).isEqualTo(assignedAt);
        });
        }

        @Test
        @Tag("TST-025")
        @DisplayName("TST-025 FR-DEL-001 SRS 3.5: unknown parcel status events retry then dead-letter without recording success")
        void unknownParcelStatusIsRetryableAndDeadLettered() throws Exception {
        UUID eventId = UUID.randomUUID();
        publish(statusEvent(UUID.randomUUID(), eventId, 2, ParcelStatus.IN_TRANSIT, Instant.now()));

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Integer deadLetterCount = rabbitTemplate.execute(
                    channel -> Math.toIntExact(channel.messageCount(
                            EventTopology.dlqName(EventTopology.DELIVERY_QUEUE))));
            assertThat(deadLetterCount).isGreaterThanOrEqualTo(1);
        });
        assertThat(jdbcTemplate.queryForObject("select count(*) from processed_events where event_id = ?",
            Long.class, eventId)).isZero();
        }

        @Test
        @Tag("TST-025")
        @DisplayName("TST-025 FR-DEL-001 FR-DEL-010: courier upsert and deactivation unassign only CREATED deliveries")
        void courierEventsUpsertAndDeactivateSelectively() throws Exception {
        UUID courierId = UUID.randomUUID();
        UUID activeEventId = UUID.randomUUID();
        publish(courierEvent(courierId, activeEventId, "Amal Perera", UserStatus.ACTIVE));
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(courierRepository.findById(courierId)).isPresent());

        publish(courierEvent(courierId, UUID.randomUUID(), "Amal Silva", UserStatus.ACTIVE,
            VehicleType.VAN, "XYZ-9876"));
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Courier updated = courierRepository.findById(courierId).orElseThrow();
            assertThat(updated.getFullName()).isEqualTo("Amal Silva");
            assertThat(updated.getVehicleType()).isEqualTo(VehicleType.VAN);
            assertThat(updated.getVehicleNumber()).isEqualTo("XYZ-9876");
        });

        Delivery created = saveDelivery(courierId, ParcelStatus.CREATED, null, COLOMBO_DAY_START);
        Delivery inTransit = saveDelivery(courierId, ParcelStatus.IN_TRANSIT, null, COLOMBO_DAY_START);
        Instant assignedAt = COLOMBO_DAY_START.minusSeconds(60);
        UUID assignedBy = UUID.randomUUID();
        assignmentRepository.save(new DeliveryAssignment(UUID.randomUUID(), created.getParcelId(), courierId,
            assignedBy, assignedAt));
        assignmentRepository.save(new DeliveryAssignment(UUID.randomUUID(), inTransit.getParcelId(), courierId,
            assignedBy, assignedAt));

        UUID deactivatedEventId = UUID.randomUUID();
        EventEnvelope<CourierChangedData> deactivated = courierEvent(courierId, deactivatedEventId,
            "Amal Silva", UserStatus.DEACTIVATED, VehicleType.VAN, "XYZ-9876");
        publish(deactivated);
        publish(deactivated);

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(courierRepository.findById(courierId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.DEACTIVATED);
            Delivery unassigned = deliveryRepository.findById(created.getParcelId()).orElseThrow();
            assertThat(unassigned.getCourierId()).isNull();
            assertThat(unassigned.getAssignedAt()).isNull();
            Delivery preserved = deliveryRepository.findById(inTransit.getParcelId()).orElseThrow();
            assertThat(preserved.getCourierId()).isEqualTo(courierId);
            assertThat(preserved.getStatus()).isEqualTo(ParcelStatus.IN_TRANSIT);
            assertThat(assignmentRepository.findByParcelIdAndUnassignedAtIsNull(created.getParcelId())).isEmpty();
            assertThat(assignmentRepository.findByParcelIdAndUnassignedAtIsNull(inTransit.getParcelId())).isPresent();
            assertThat(jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where routing_key = 'delivery.unassigned'", Long.class))
                .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                "select payload->'data'->>'reason' from outbox_events where routing_key = 'delivery.unassigned'",
                String.class)).isEqualTo("COURIER_DEACTIVATED");
            assertThat(jdbcTemplate.queryForObject(
                "select payload->'data'->>'parcelId' from outbox_events where routing_key = 'delivery.unassigned'",
                String.class)).isEqualTo(created.getParcelId().toString());
            assertThat(jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where routing_key = 'delivery.unassigned' "
                    + "and payload->'data'->>'unassignedAt' is not null", Long.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("select count(*) from processed_events where event_id = ?",
                Long.class, deactivatedEventId)).isEqualTo(1);
        });
        }

        private void publishParcelCreated(UUID parcelId) throws Exception {
        publish(envelope(EventTypes.PARCEL_CREATED, UUID.randomUUID(),
            new ParcelCreatedData(parcelId, trackingNumber(), ParcelStatus.CREATED, OwnerType.CUSTOMER,
                UUID.randomUUID(), null, null, "COLOMBO", "Colombo", "Western", "KANDY", "Kandy",
                "Central", "Colombo", "Kandy", COLOMBO_DAY_START)));
        }

        private EventEnvelope<ParcelStatusChangedData> statusEvent(UUID parcelId, UUID eventId, int sequence,
                                    ParcelStatus status, Instant changedAt) {
        return envelope(EventTypes.PARCEL_STATUS_CHANGED, eventId,
            new ParcelStatusChangedData(parcelId, trackingNumber(), sequence, ParcelStatus.CREATED, status,
                changedAt, null, com.swiftdrop.common.enums.ActorRole.SYSTEM, null, null,
                OwnerType.CUSTOMER, UUID.randomUUID(), null, null));
        }

        private EventEnvelope<CourierChangedData> courierEvent(UUID courierId, UUID eventId, String name,
                                     UserStatus status) {
        return courierEvent(courierId, eventId, name, status, VehicleType.MOTORBIKE, "ABC-1234");
        }

        private EventEnvelope<CourierChangedData> courierEvent(UUID courierId, UUID eventId, String name,
                                     UserStatus status, VehicleType vehicleType,
                                     String vehicleNumber) {
        return envelope(EventTypes.COURIER_CHANGED, eventId,
            new CourierChangedData(courierId, name, status, vehicleType, vehicleNumber));
        }

        private <T> EventEnvelope<T> envelope(String eventType, UUID eventId, T data) {
        return new EventEnvelope<>(eventId, eventType, EventEnvelope.SCHEMA_VERSION,
            Instant.now(), UUID.randomUUID(), data);
        }

        private void publish(EventEnvelope<?> event) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        rabbitTemplate.send(EventTopology.EXCHANGE, event.eventType(),
            new Message(objectMapper.writeValueAsBytes(event), properties));
        }

        private void awaitDelivery(UUID parcelId, java.util.function.Predicate<Delivery> condition) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(deliveryRepository.findById(parcelId)).hasValueSatisfying(delivery ->
                assertThat(condition.test(delivery)).isTrue()));
        }

        private static String trackingNumber() {
        return "SD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        }

    private Delivery saveDelivery(UUID courierId, ParcelStatus status, Instant terminalAt, Instant assignedAt) {
        UUID parcelId = UUID.randomUUID();
        Delivery delivery = new Delivery(parcelId, "SD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                status, status.isTerminal() ? 2 : 1, OwnerType.CUSTOMER, UUID.randomUUID(), null,
                "COLOMBO", "Colombo", "KANDY", "Kandy", "Colombo", "Kandy", COLOMBO_DAY_START);
        delivery.assignTo(courierId, assignedAt);
        if (status.isTerminal()) {
            delivery.updateStatus(status, 2, terminalAt);
        }
        return deliveryRepository.save(delivery);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the row-lock test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Row-lock test interrupted", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfigurationForDelivery {

        @Bean
        @Primary
        JwtPublicKeyProvider testJwtPublicKeyProvider() {
            return TOKENS.publicKeyProvider();
        }

        @Bean
        @Primary
        Clock testDeliveryClock() {
            return TEST_CLOCK;
        }
    }
}
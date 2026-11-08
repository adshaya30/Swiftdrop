package com.swiftdrop.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.enums.UserStatus;
import com.swiftdrop.common.enums.VehicleType;
import com.swiftdrop.common.security.JwtPublicKeyProvider;
import com.swiftdrop.common.testsupport.TestTokens;
import com.swiftdrop.delivery.domain.Courier;
import com.swiftdrop.delivery.domain.CourierDeliveryQueries;
import com.swiftdrop.delivery.domain.CourierRepository;
import com.swiftdrop.delivery.domain.Delivery;
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
    private CourierDeliveryQueries courierDeliveryQueries;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    }

    @BeforeEach
    void clearTables() {
        deliveryRepository.deleteAll();
        courierRepository.deleteAll();
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
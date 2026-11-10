package com.swiftdrop.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.enums.UserStatus;
import com.swiftdrop.common.enums.VehicleType;
import com.swiftdrop.common.security.JwtPublicKeyProvider;
import com.swiftdrop.delivery.domain.Courier;
import com.swiftdrop.delivery.domain.CourierRepository;
import com.swiftdrop.delivery.domain.Delivery;
import com.swiftdrop.delivery.domain.DeliveryAssignmentRepository;
import com.swiftdrop.delivery.domain.DeliveryRepository;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(DeliveryAdminApiIntegrationTest.TestConfigurationForDelivery.class)
class DeliveryAdminApiIntegrationTest {

    private static final Instant COLOMBO_TODAY_START = Instant.parse("2026-11-07T18:30:00Z");
        private static final RSAPublicKey TEST_PUBLIC_KEY = generatePublicKey();
    private static final String INTERNAL_KEY = "delivery-admin-test-internal-key-0123456789";

    @Container
    private static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    private static final RabbitMQContainer BROKER = new RabbitMQContainer("rabbitmq:4-management-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CourierRepository courierRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryAssignmentRepository assignmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        jdbcTemplate.execute("TRUNCATE TABLE delivery_assignments, deliveries, couriers, processed_events, outbox_events "
                + "RESTART IDENTITY CASCADE");
    }

    @Test
    @Tag("TST-029")
    @DisplayName("TST-029 FR-DEL-002 FR-DEL-012 AT-17: assignment, reassignment, history, outbox and audit are atomic")
    void assignsAndReassignsWithTransactionalEventsAndHistory() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID firstCourierId = saveCourier("Amal Perera", VehicleType.MOTORBIKE, UserStatus.ACTIVE);
        UUID secondCourierId = saveCourier("Nimal Silva", VehicleType.VAN, UserStatus.ACTIVE);
        Delivery delivery = saveDelivery(null, ParcelStatus.CREATED, null);
        assign(delivery.getParcelId(), firstCourierId, adminId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courier.id").value(firstCourierId.toString()))
                .andExpect(jsonPath("$.status").value("CREATED"));
        assign(delivery.getParcelId(), secondCourierId, adminId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courier.id").value(secondCourierId.toString()));

        var assignments = assignmentRepository.findByParcelIdOrderByAssignedAtAsc(delivery.getParcelId());
        assertThat(assignments).hasSize(2);
        assertThat(assignments.get(0).getUnassignedAt()).isNotNull();
        assertThat(assignments.get(1).getUnassignedAt()).isNull();
        assertThat(deliveryRepository.findById(delivery.getParcelId()).orElseThrow().getStatus())
                .isEqualTo(ParcelStatus.CREATED);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where routing_key = 'delivery.assigned'", Long.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where routing_key = 'audit.recorded' "
                        + "and payload->'data'->>'action' = 'DELIVERY_ASSIGNED'", Long.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select payload->'data'->>'courierFirstName' from outbox_events "
                        + "where routing_key = 'delivery.assigned' order by created_at limit 1", String.class))
                .isEqualTo("Amal");

        mockMvc.perform(get("/api/v1/admin/couriers/{id}/deliveries", firstCourierId)
                        .param("scope", "active").with(admin(adminId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(get("/api/v1/admin/deliveries/{id}", delivery.getParcelId())
                        .with(admin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments.length()").value(2))
                .andExpect(jsonPath("$.assignments[0].courierName").value("Amal Perera"));
    }

    @Test
    @Tag("TST-029")
    @DisplayName("TST-029 FR-DEL-002: same-courier assignment is a no-op; unknown and ineligible resources fail")
    void assignmentIsIdempotentAndRejectsUnknownOrIneligibleResources() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID activeCourier = saveCourier("Amal Perera", VehicleType.MOTORBIKE, UserStatus.ACTIVE);
        UUID inactiveCourier = saveCourier("Nimal Silva", VehicleType.VAN, UserStatus.DEACTIVATED);
        Delivery created = saveDelivery(null, ParcelStatus.CREATED, null);
        Delivery pickedUp = saveDelivery(null, ParcelStatus.PICKED_UP, null);

        assign(created.getParcelId(), activeCourier, adminId).andExpect(status().isOk());
        assign(created.getParcelId(), activeCourier, adminId).andExpect(status().isOk());
        assertThat(assignmentRepository.findByParcelIdOrderByAssignedAtAsc(created.getParcelId())).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where routing_key = 'delivery.assigned'", Long.class)).isEqualTo(1);

        assign(created.getParcelId(), inactiveCourier, adminId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARCEL_NOT_ASSIGNABLE"));
        assign(pickedUp.getParcelId(), activeCourier, adminId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARCEL_NOT_ASSIGNABLE"));
        assign(UUID.randomUUID(), activeCourier, adminId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assign(created.getParcelId(), UUID.randomUUID(), adminId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @Tag("TST-029")
    @DisplayName("TST-029 FR-DEL-009 AT-27: delivery filters and queue pagination use Colombo-day workload counts")
    void filtersDeliveryListAndCalculatesWorkloads() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID firstCourier = saveCourier("Amal Perera", VehicleType.MOTORBIKE, UserStatus.ACTIVE);
        UUID secondCourier = saveCourier("Nimal Silva", VehicleType.VAN, UserStatus.ACTIVE);
        Delivery oldUnassigned = saveDelivery(null, ParcelStatus.CREATED, null,
                COLOMBO_TODAY_START.minus(Duration.ofHours(5)));
        saveDelivery(null, ParcelStatus.CREATED, null, COLOMBO_TODAY_START.minus(Duration.ofHours(1)));
        Delivery inTransit = saveDelivery(firstCourier, ParcelStatus.IN_TRANSIT, null,
                COLOMBO_TODAY_START.minus(Duration.ofHours(2)));
        saveDelivery(firstCourier, ParcelStatus.DELIVERED, COLOMBO_TODAY_START.plus(Duration.ofHours(1)),
                COLOMBO_TODAY_START.minus(Duration.ofHours(3)));
        saveDelivery(secondCourier, ParcelStatus.FAILED, COLOMBO_TODAY_START.plus(Duration.ofHours(2)),
                COLOMBO_TODAY_START.minus(Duration.ofHours(4)));

        mockMvc.perform(get("/api/v1/admin/deliveries").param("assigned", "false").param("status", "CREATED")
                        .param("page", "0").param("size", "1").with(admin(adminId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].parcelId").value(oldUnassigned.getParcelId().toString()))
                .andExpect(jsonPath("$.totalItems").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/api/v1/admin/deliveries").param("courierId", firstCourier.toString())
                        .param("status", "IN_TRANSIT").param("q", inTransit.getTrackingNumber().substring(0, 7))
                        .param("from", "2026-11-07").param("to", "2026-11-08")
                        .with(admin(adminId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));

        mockMvc.perform(get("/api/v1/admin/couriers").with(admin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].activeDeliveries.IN_TRANSIT").value(1))
                .andExpect(jsonPath("$[1].deliveredToday").value(1))
                .andExpect(jsonPath("$[0].failedToday").value(1));
        mockMvc.perform(get("/api/v1/admin/dashboard/dispatch").with(admin(adminId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unassigned").value(2))
                .andExpect(jsonPath("$.inProgress").value(1)).andExpect(jsonPath("$.activeCouriers").value(2));
        mockMvc.perform(get("/internal/v1/couriers/{id}/active-count", firstCourier)
                        .header("X-Internal-Key", INTERNAL_KEY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inProgress").value(1));
    }

    @Test
    @Tag("TST-033")
    @DisplayName("TST-033 FR-DEL-002 FR-DEL-009: admin endpoints and internal active-count enforce their credentials")
    void protectsAdminAndInternalEndpoints() throws Exception {
        UUID courierId = UUID.randomUUID();
        String dashboard = "/api/v1/admin/dashboard/dispatch";
        mockMvc.perform(get(dashboard)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(dashboard).with(jwt().jwt(token -> token.claim("role", "CUSTOMER")
                        .claim("typ", "user").subject(UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get(dashboard).with(jwt().jwt(token -> token.claim("role", "MERCHANT")
                        .claim("typ", "client").claim("scope", "orders:read")
                        .subject(UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/internal/v1/couriers/{id}/active-count", courierId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/v1/couriers/{id}/active-count", courierId)
                        .header("X-Internal-Key", "wrong-internal-key"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/v1/couriers/{id}/active-count", courierId)
                        .header("X-Internal-Key", INTERNAL_KEY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inProgress").value(0));
    }

    @Test
    @Tag("TST-029")
    @DisplayName("TST-029 FR-DEL-002: concurrent assignments serialize and leave only one open assignment")
    void concurrentAssignmentsLeaveOneOpenHistoryRow() throws Exception {
        UUID courierA = saveCourier("Amal Perera", VehicleType.MOTORBIKE, UserStatus.ACTIVE);
        UUID courierB = saveCourier("Nimal Silva", VehicleType.VAN, UserStatus.ACTIVE);
        UUID parcelId = saveDelivery(null, ParcelStatus.CREATED, null).getParcelId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
                            Future<?> first = executor.submit(() -> requestAssignment(parcelId, courierA));
                            Future<?> second = executor.submit(() -> requestAssignment(parcelId, courierB));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        var history = assignmentRepository.findByParcelIdOrderByAssignedAtAsc(parcelId);
        assertThat(history).hasSize(2).filteredOn(item -> item.getUnassignedAt() == null).hasSize(1);
        assertThat(deliveryRepository.findById(parcelId).orElseThrow().getCourierId()).isIn(courierA, courierB);
    }

        private org.springframework.test.web.servlet.ResultActions assign(UUID parcelId, UUID courierId, UUID adminId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/admin/deliveries/{id}/assign", parcelId)
                                .with(admin(adminId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(Map.of("courierId", courierId))));
    }

        private void requestAssignment(UUID parcelId, UUID courierId) {
        try {
                        assign(parcelId, courierId, UUID.randomUUID()).andExpect(status().isOk());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

        private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(UUID adminId) {
                return jwt().jwt(token -> token.subject(adminId.toString())
                                .claim("role", "ADMIN")
                        .claim("typ", "user"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }

        private static RSAPublicKey generatePublicKey() {
                try {
                        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                        generator.initialize(2048);
                        return (RSAPublicKey) generator.generateKeyPair().getPublic();
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Could not create test JWT public key", exception);
        }
        }

    private UUID saveCourier(String name, VehicleType vehicleType, UserStatus status) {
        UUID id = UUID.randomUUID();
        courierRepository.save(new Courier(id, name, vehicleType, "ABC-1234", status));
        return id;
    }

    private Delivery saveDelivery(UUID courierId, ParcelStatus status, Instant terminalAt) {
        return saveDelivery(courierId, status, terminalAt, COLOMBO_TODAY_START.minus(Duration.ofHours(1)));
    }

    private Delivery saveDelivery(UUID courierId, ParcelStatus status, Instant terminalAt, Instant createdAt) {
        UUID parcelId = UUID.randomUUID();
        Delivery delivery = new Delivery(parcelId,
                "SD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10), status,
                status.isTerminal() ? 2 : 1, OwnerType.CUSTOMER, UUID.randomUUID(), null,
                "COLOMBO", "Colombo", "KANDY", "Kandy", "Colombo", "Kandy", createdAt);
        if (courierId != null) delivery.assignTo(courierId, createdAt);
        if (status.isTerminal()) delivery.updateStatus(status, 2, terminalAt);
        return deliveryRepository.save(delivery);
    }
        @TestConfiguration(proxyBeanMethods = false)
        static class TestConfigurationForDelivery {

                @Bean
                @Primary
                JwtPublicKeyProvider testJwtPublicKeyProvider() {
                        return () -> TEST_PUBLIC_KEY;
                }

                @Bean
                @Primary
                Clock testDeliveryClock() {
                        return Clock.fixed(COLOMBO_TODAY_START, ZoneOffset.UTC);
                }
        }
}
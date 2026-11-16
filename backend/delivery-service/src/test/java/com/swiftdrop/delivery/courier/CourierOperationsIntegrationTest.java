package com.swiftdrop.delivery.courier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.enums.UserStatus;
import com.swiftdrop.common.enums.VehicleType;
import com.swiftdrop.common.security.JwtPublicKeyProvider;
import com.swiftdrop.common.testsupport.TestTokens;
import com.swiftdrop.delivery.courier.entity.DeliveryProof;
import com.swiftdrop.delivery.courier.entity.ProofPhoto;
import com.swiftdrop.delivery.courier.repository.DeliveryProofRepository;
import com.swiftdrop.delivery.courier.repository.ProofPhotoRepository;
import com.swiftdrop.delivery.domain.Courier;
import com.swiftdrop.delivery.domain.CourierRepository;
import com.swiftdrop.delivery.domain.Delivery;
import com.swiftdrop.delivery.domain.DeliveryRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(CourierOperationsIntegrationTest.TestConfig.class)
class CourierOperationsIntegrationTest {

    private static final Instant COLOMBO_TODAY_START = Instant.parse("2026-11-07T18:30:00Z");
    private static final String INTERNAL_KEY = "test-internal-api-key-32-chars-long-abc";
    private static final TestTokens TOKENS = TestTokens.create();

    private static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    @Container
    private static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    private static final RabbitMQContainer BROKER = new RabbitMQContainer("rabbitmq:4-management-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private CourierRepository courierRepository;

    @Autowired
    private DeliveryProofRepository proofRepository;

    @Autowired
    private ProofPhotoRepository photoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void startWiremock() {
        WIREMOCK.start();
    }

    @AfterAll
    static void stopWiremock() {
        WIREMOCK.stop();
    }

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
        registry.add("swiftdrop.parcel-service-url", WIREMOCK::baseUrl);
        registry.add("OUTBOX_POLL_INTERVAL_MS", () -> "60000");
    }

    @BeforeEach
    void setup() {
        WIREMOCK.resetAll();
        jdbcTemplate.execute("TRUNCATE TABLE proof_photos, delivery_proofs, delivery_assignments, deliveries, couriers, processed_events, outbox_events RESTART IDENTITY CASCADE");
    }

    @Test
    @Tag("TST-022")
    @DisplayName("TST-022 FR-DEL-005 FR-DEL-007 Flow 3: proof and parcel transition succeed together")
    void proofAndTransitionSucceedTogether() throws Exception {
        UUID courierId = saveCourier("Kamal Courier", VehicleType.MOTORBIKE);
        Delivery delivery = saveDelivery(courierId, ParcelStatus.OUT_FOR_DELIVERY, 2);
        UUID parcelId = delivery.getParcelId();

        byte[] validJpeg = createValidJpeg(200, 200);
        MockMultipartFile photo = new MockMultipartFile("photo", "proof.jpg", "image/jpeg", validJpeg);

        String transitionSuccessJson = """
                {
                  "parcelId": "%s",
                  "status": "DELIVERED",
                  "sequence": 3,
                  "statusChangedAt": "2026-11-08T10:00:00Z"
                }
                """.formatted(parcelId);

        WIREMOCK.stubFor(WireMock.post(WireMock.urlEqualTo("/internal/v1/parcels/" + parcelId + "/transition"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(transitionSuccessJson)));

        mockMvc.perform(multipart("/api/v1/courier/deliveries/{parcelId}/status", parcelId)
                        .file(photo)
                        .param("status", "DELIVERED")
                        .param("recipientName", "Sunil Silva")
                        .param("note", "Handed over at front door")
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.courier().userId(courierId).bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelId").value(parcelId.toString()))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.sequence").value(3));

        // Verify DB updates
        Delivery updated = deliveryRepository.findById(parcelId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(updated.getStatusSequence()).isEqualTo(3);
        assertThat(updated.getTerminalAt()).isNotNull();

        DeliveryProof proof = proofRepository.findById(parcelId).orElseThrow();
        assertThat(proof.getRecipientName()).isEqualTo("Sunil Silva");
        assertThat(proof.getNote()).isEqualTo("Handed over at front door");
        assertThat(proof.isHasPhoto()).isTrue();

        ProofPhoto storedPhoto = photoRepository.findById(parcelId).orElseThrow();
        assertThat(storedPhoto.getContentType()).isEqualTo("image/jpeg");
        assertThat(storedPhoto.getData()).isNotEmpty();
    }

    @Test
    @Tag("TST-022")
    @DisplayName("TST-022 FR-DEL-005 Flow 3: failed transition (409 and 5xx) leaves no proof")
    void failedTransitionLeavesNoProof() throws Exception {
        UUID courierId = saveCourier("Kamal Courier", VehicleType.MOTORBIKE);
        Delivery delivery = saveDelivery(courierId, ParcelStatus.OUT_FOR_DELIVERY, 2);
        UUID parcelId = delivery.getParcelId();

        MockMultipartFile photo = new MockMultipartFile("photo", "proof.jpg", "image/jpeg", createValidJpeg(100, 100));

        // Stub 409 CONCURRENT_UPDATE from parcel-service
        String problem409 = """
                {
                  "type": "https://swiftdrop.com/errors/CONCURRENT_UPDATE",
                  "title": "Concurrent update",
                  "status": 409,
                  "detail": "Parcel was modified concurrently",
                  "code": "CONCURRENT_UPDATE"
                }
                """;
        WIREMOCK.stubFor(WireMock.post(WireMock.urlEqualTo("/internal/v1/parcels/" + parcelId + "/transition"))
                .willReturn(WireMock.aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody(problem409)));

        // Re-read returns previous status (not applied)
        String reReadJson = """
                {
                  "id": "%s",
                  "trackingNumber": "%s",
                  "status": "OUT_FOR_DELIVERY",
                  "statusSequence": 2
                }
                """.formatted(parcelId, delivery.getTrackingNumber());
        WIREMOCK.stubFor(WireMock.get(WireMock.urlEqualTo("/internal/v1/parcels/" + parcelId))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(reReadJson)));

        mockMvc.perform(multipart("/api/v1/courier/deliveries/{parcelId}/status", parcelId)
                        .file(photo)
                        .param("status", "DELIVERED")
                        .param("recipientName", "Sunil Silva")
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.courier().userId(courierId).bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));

        // Verify transaction rolled back: NO proof, NO photo, status unchanged
        assertThat(proofRepository.findById(parcelId)).isEmpty();
        assertThat(photoRepository.findById(parcelId)).isEmpty();
        Delivery unchanged = deliveryRepository.findById(parcelId).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(ParcelStatus.OUT_FOR_DELIVERY);
    }

    @Test
    @Tag("TST-022")
    @DisplayName("TST-022 FR-DEL-005 Flow 3: lost transition response with matching re-read still stores proof and succeeds")
    void lostTransitionResponseWithMatchingReReadSucceeds() throws Exception {
        UUID courierId = saveCourier("Kamal Courier", VehicleType.MOTORBIKE);
        Delivery delivery = saveDelivery(courierId, ParcelStatus.OUT_FOR_DELIVERY, 2);
        UUID parcelId = delivery.getParcelId();

        MockMultipartFile photo = new MockMultipartFile("photo", "proof.jpg", "image/jpeg", createValidJpeg(100, 100));

        // Transition call fails with 503 (e.g. timeout / connection drop after applying update)
        WIREMOCK.stubFor(WireMock.post(WireMock.urlEqualTo("/internal/v1/parcels/" + parcelId + "/transition"))
                .willReturn(WireMock.aResponse().withStatus(503)));

        // Re-read verifies the parcel was indeed transitioned to DELIVERED with sequence = 3 (local sequence + 1)
        String reReadSuccessJson = """
                {
                  "id": "%s",
                  "trackingNumber": "%s",
                  "status": "DELIVERED",
                  "statusSequence": 3,
                  "updatedAt": "2026-11-08T10:05:00Z"
                }
                """.formatted(parcelId, delivery.getTrackingNumber());
        WIREMOCK.stubFor(WireMock.get(WireMock.urlEqualTo("/internal/v1/parcels/" + parcelId))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(reReadSuccessJson)));

        mockMvc.perform(multipart("/api/v1/courier/deliveries/{parcelId}/status", parcelId)
                        .file(photo)
                        .param("status", "DELIVERED")
                        .param("recipientName", "Sunil Silva")
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.courier().userId(courierId).bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.sequence").value(3));

        // Verify committed
        assertThat(proofRepository.findById(parcelId)).isPresent();
        assertThat(photoRepository.findById(parcelId)).isPresent();
        Delivery updated = deliveryRepository.findById(parcelId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(updated.getStatusSequence()).isEqualTo(3);
    }

    @Test
    @Tag("TST-022")
    @DisplayName("TST-022 FR-DEL-006: photo validation - content inspection, sizing, and status restriction")
    void photoValidationTests() throws Exception {
        UUID courierId = saveCourier("Kamal Courier", VehicleType.MOTORBIKE);
        Delivery delivery = saveDelivery(courierId, ParcelStatus.OUT_FOR_DELIVERY, 2);
        UUID parcelId = delivery.getParcelId();

        // 1. Renamed text file rejected as 415 UNSUPPORTED_MEDIA_TYPE
        MockMultipartFile textFile = new MockMultipartFile("photo", "fake.jpg", "image/jpeg",
                "Hello, I am a plain text file, not a jpeg!".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/courier/deliveries/{parcelId}/status", parcelId)
                        .file(textFile)
                        .param("status", "DELIVERED")
                        .param("recipientName", "Sunil Silva")
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.courier().userId(courierId).bearer()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        // 2. Photo sent with non-DELIVERED status (e.g. FAILED) rejected as 400
        MockMultipartFile validJpeg = new MockMultipartFile("photo", "proof.jpg", "image/jpeg", createValidJpeg(100, 100));
        mockMvc.perform(multipart("/api/v1/courier/deliveries/{parcelId}/status", parcelId)
                        .file(validJpeg)
                        .param("status", "FAILED")
                        .param("failureReason", "RECIPIENT_UNAVAILABLE")
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.courier().userId(courierId).bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 3. PNG accepted and re-encoded to JPEG <= 1600 px with metadata stripped
        byte[] largePng = createValidPng(2000, 1000);
        MockMultipartFile pngFile = new MockMultipartFile("photo", "large.png", "image/png", largePng);

        WIREMOCK.stubFor(WireMock.post(WireMock.urlEqualTo("/internal/v1/parcels/" + parcelId + "/transition"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "parcelId": "%s",
                                  "status": "DELIVERED",
                                  "sequence": 3,
                                  "statusChangedAt": "2026-11-08T10:00:00Z"
                                }
                                """.formatted(parcelId))));

        mockMvc.perform(multipart("/api/v1/courier/deliveries/{parcelId}/status", parcelId)
                        .file(pngFile)
                        .param("status", "DELIVERED")
                        .param("recipientName", "Sunil Silva")
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.courier().userId(courierId).bearer()))
                .andExpect(status().isOk());

        ProofPhoto stored = photoRepository.findById(parcelId).orElseThrow();
        assertThat(stored.getContentType()).isEqualTo("image/jpeg");
        BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(stored.getData()));
        assertThat(decoded.getWidth()).isEqualTo(1600);
        assertThat(decoded.getHeight()).isEqualTo(800);
    }

    @Test
    @Tag("TST-030")
    @DisplayName("TST-030 FR-DEL-008: courier cannot read or update another courier's or unassigned parcel (404)")
    void courierCannotAccessOtherCourierOrUnassignedParcels() throws Exception {
        UUID courier1 = saveCourier("Courier One", VehicleType.MOTORBIKE);
        UUID courier2 = saveCourier("Courier Two", VehicleType.VAN);

        Delivery deliveryForCourier2 = saveDelivery(courier2, ParcelStatus.OUT_FOR_DELIVERY, 2);
        Delivery unassignedDelivery = saveDelivery(null, ParcelStatus.CREATED, 1);

        // Courier 1 cannot GET Courier 2's parcel
        mockMvc.perform(get("/api/v1/courier/deliveries/{id}", deliveryForCourier2.getParcelId())
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.courier().userId(courier1).bearer()))
                .andExpect(status().isNotFound());

        // Courier 1 cannot update Courier 2's parcel
        mockMvc.perform(multipart("/api/v1/courier/deliveries/{id}/status", deliveryForCourier2.getParcelId())
                        .param("status", "DELIVERED")
                        .param("recipientName", "Test Person")
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.courier().userId(courier1).bearer()))
                .andExpect(status().isNotFound());

        // Courier 1 cannot GET unassigned parcel
        mockMvc.perform(get("/api/v1/courier/deliveries/{id}", unassignedDelivery.getParcelId())
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.courier().userId(courier1).bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Tag("TST-030")
    @DisplayName("TST-030 FR-DEL-008: courier cannot access admin, merchant or customer endpoints (403)")
    void courierCannotAccessAdminEndpoints() throws Exception {
        UUID courierId = saveCourier("Courier One", VehicleType.MOTORBIKE);

        mockMvc.perform(get("/api/v1/admin/deliveries")
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.courier().userId(courierId).bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("TST-030")
    @DisplayName("TST-030 FR-DEL-007 BR-06: proof access matrix")
    void proofAccessMatrix() throws Exception {
        UUID courierId = saveCourier("Courier One", VehicleType.MOTORBIKE);
        UUID otherCourierId = saveCourier("Courier Two", VehicleType.MOTORBIKE);
        UUID customerId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        // Customer-owned delivered parcel
        Delivery delivery = saveDeliveryWithOwner(courierId, customerId, null, ParcelStatus.DELIVERED, 3);
        UUID parcelId = delivery.getParcelId();

        proofRepository.save(new DeliveryProof(parcelId, "Recipient", "At gate", true, Instant.now()));
        photoRepository.save(new ProofPhoto(parcelId, "image/jpeg", createValidJpeg(50, 50)));

        // 1. Owner customer can access (200)
        mockMvc.perform(get("/api/v1/proofs/{id}", parcelId)
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.customer().userId(customerId).bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientName").value("Recipient"));

        mockMvc.perform(get("/api/v1/proofs/{id}/photo", parcelId)
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.customer().userId(customerId).bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"));

        // 2. Other customer gets 404
        mockMvc.perform(get("/api/v1/proofs/{id}", parcelId)
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.customer().userId(otherCustomerId).bearer()))
                .andExpect(status().isNotFound());

        // 3. Assigned courier can access (200)
        mockMvc.perform(get("/api/v1/proofs/{id}", parcelId)
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.courier().userId(courierId).bearer()))
                .andExpect(status().isOk());

        // 4. Other courier gets 404
        mockMvc.perform(get("/api/v1/proofs/{id}", parcelId)
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.courier().userId(otherCourierId).bearer()))
                .andExpect(status().isNotFound());

        // 5. Admin can access (200)
        mockMvc.perform(get("/api/v1/proofs/{id}", parcelId)
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.admin().bearer()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Table 2.4 and Table 2.3 stage validations: invalid transitions and reason rules")
    void statusTransitionAndFailureReasonRules() throws Exception {
        UUID courierId = saveCourier("Kamal Courier", VehicleType.MOTORBIKE);
        Delivery delivery = saveDelivery(courierId, ParcelStatus.CREATED, 1);
        UUID parcelId = delivery.getParcelId();

        // 1. CREATED cannot transition directly to DELIVERED (409)
        mockMvc.perform(multipart("/api/v1/courier/deliveries/{id}/status", parcelId)
                        .param("status", "DELIVERED")
                        .param("recipientName", "Kamala")
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.courier().userId(courierId).bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        // 2. FAILED with OTHER requires note 5-500 chars (missing note -> 400)
        mockMvc.perform(multipart("/api/v1/courier/deliveries/{id}/status", parcelId)
                        .param("status", "FAILED")
                        .param("failureReason", "OTHER")
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.courier().userId(courierId).bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 3. FAILED with invalid reason for CREATED stage (e.g. RECIPIENT_REFUSED is only allowed on OUT_FOR_DELIVERY) -> 400
        mockMvc.perform(multipart("/api/v1/courier/deliveries/{id}/status", parcelId)
                        .param("status", "FAILED")
                        .param("failureReason", "RECIPIENT_REFUSED")
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.courier().userId(courierId).bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("FR-DEL-003: GET /courier/deliveries with scopes today, active, completed")
    void courierDeliveriesListScopes() throws Exception {
        UUID courierId = saveCourier("Kamal Courier", VehicleType.MOTORBIKE);
        saveDelivery(courierId, ParcelStatus.CREATED, 1);
        saveDelivery(courierId, ParcelStatus.OUT_FOR_DELIVERY, 2);

        mockMvc.perform(get("/api/v1/courier/deliveries?scope=active")
                        .header(HttpHeaders.AUTHORIZATION, TOKENS.courier().userId(courierId).bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    // --- Helpers ---

    private UUID saveCourier(String name, VehicleType vehicleType) {
        UUID id = UUID.randomUUID();
        Courier courier = new Courier(id, name, vehicleType, "WP-CAD-1234", UserStatus.ACTIVE);
        courierRepository.save(courier);
        return id;
    }

    private Delivery saveDelivery(UUID courierId, ParcelStatus status, int sequence) {
        return saveDeliveryWithOwner(courierId, UUID.randomUUID(), null, status, sequence);
    }

    private Delivery saveDeliveryWithOwner(UUID courierId, UUID customerId, UUID merchantId, ParcelStatus status, int sequence) {
        UUID parcelId = UUID.randomUUID();
        OwnerType ownerType = customerId != null ? OwnerType.CUSTOMER : OwnerType.MERCHANT;
        Delivery delivery = new Delivery(
                parcelId,
                "SD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(),
                status,
                sequence,
                ownerType,
                customerId,
                merchantId,
                "COLOMBO", "Colombo",
                "KANDY", "Kandy",
                "Colombo 03", "Kandy Town",
                Instant.now().minus(Duration.ofHours(2))
        );
        if (courierId != null) {
            delivery.assignTo(courierId, Instant.now().minus(Duration.ofHours(1)));
        }
        if (status.isTerminal()) {
            delivery.updateStatus(status, sequence, Instant.now());
        }
        return deliveryRepository.save(delivery);
    }

    private byte[] createValidJpeg(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    private byte[] createValidPng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(255, 0, 0, 128)); // Semi-transparent red
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        @Primary
        JwtPublicKeyProvider testJwtPublicKeyProvider() {
            return TOKENS.publicKeyProvider();
        }

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(COLOMBO_TODAY_START, ZoneOffset.UTC);
        }
    }
}

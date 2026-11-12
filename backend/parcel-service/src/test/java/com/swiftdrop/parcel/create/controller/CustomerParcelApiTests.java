package com.swiftdrop.parcel.create.controller;

import com.fasterxml.jackson.databind.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.swiftdrop.common.enums.*;
import com.swiftdrop.common.events.*;
import com.swiftdrop.common.events.data.ParcelCreatedData;
import com.swiftdrop.parcel.create.dto.*;
import com.swiftdrop.parcel.create.service.*;
import com.swiftdrop.parcel.domain.ParcelRepository;
import com.swiftdrop.parcel.support.AbstractParcelDatabaseTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.swiftdrop.parcel.support.ParcelFixtures.parcel;
import static com.swiftdrop.parcel.support.ParcelTestConfiguration.TOKENS;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("TST-021")
@Tag("TST-031")
class CustomerParcelApiTests extends AbstractParcelDatabaseTest {
    private static final WireMockServer PRICING = new WireMockServer(options().dynamicPort());
    static { PRICING.start(); }
    @DynamicPropertySource
    static void pricingUrl(DynamicPropertyRegistry registry) {
        registry.add("parcel.pricing-service-url", PRICING::baseUrl);
    }
    @AfterAll static void stopPricing() { PRICING.stop(); }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ParcelRepository parcels;
    @Autowired ParcelCreationService creation;
    @Autowired IdempotencyService keys;
    @MockitoBean TrackingNumberGenerator tracking;
    @MockitoSpyBean OutboxService outbox;
    private final UUID owner = UUID.randomUUID();
    private final UUID foreign = UUID.randomUUID();
    private static final String PATH = "/api/v1/customer/parcels";

    @BeforeEach
    void prepare() {
        PRICING.resetAll();
        when(tracking.generate()).thenAnswer(call -> new TrackingNumberGenerator().generate());
        price(800);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM outbox_events WHERE payload->'data'->>'parcelId' IN "
                + "(SELECT id::text FROM parcels WHERE customer_id IN (?,?))", owner, foreign);
        jdbc.update("DELETE FROM idempotency_keys WHERE parcel_id IN "
                + "(SELECT id FROM parcels WHERE customer_id IN (?,?))", owner, foreign);
        jdbc.update("DELETE FROM parcels WHERE customer_id IN (?,?)", owner, foreign);
    }

    private void price(int total) {
        PRICING.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/internal/v1/pricing/calculate")
                .willReturn(okJson("""
                        {"currency":"LKR","originZone":{"code":"WESTERN","name":"Western"},
                        "destinationZone":{"code":"CENTRAL","name":"Central"},"baseFee":500.00,
                        "weightFee":150.00,"sizeSurcharge":150.00,"discountAmount":0.00,"total":%d.00,
                        "originDistrict":{"code":"COLOMBO","name":"Colombo"},
                        "destinationDistrict":{"code":"KANDY","name":"Kandy"},"subtotal":%d.00,"discountPercent":0.00}
                        """.formatted(total, total))));
    }

    private String body() {
        return """
                {"pickup":{"contactName":"Private Sender","contactPhone":"0771234567",
                "addressLine1":"Private pickup address","city":"Colombo","districtCode":"COLOMBO"},
                "dropoff":{"contactName":"Private Receiver","contactPhone":"0712345678",
                "addressLine1":"Private dropoff address","city":"Kandy","districtCode":"KANDY",
                "contactEmail":"private@example.invalid"},
                "parcel":{"weightKg":2.00,"size":"MEDIUM","description":"Books"},
                "deliveryInstructions":"Private instructions","expectedFee":800.00}
                """;
    }

    private ResultActions create(String key, String body) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(PATH)
                .header("Authorization", TOKENS.customer().userId(owner).bearer())
                .contentType("application/json").content(body);
        if (key != null) request.header("Idempotency-Key", key);
        return mvc.perform(request);
    }

    private int parcelCount() {
        return jdbc.queryForObject("SELECT count(*) FROM parcels WHERE customer_id=?", Integer.class, owner);
    }

    @Test
    void createsSnapshotLocationAndPrivateDataFreeOutboxAtomically() throws Exception {
        var response = create("success", body()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.fee.total").value(800))
                .andExpect(jsonPath("$.merchantReference").doesNotExist()).andReturn().getResponse();
        var result = json.readTree(response.getContentAsString());
        UUID id = UUID.fromString(result.get("id").asText());
        assertThat(response.getHeader("Location")).isEqualTo(PATH + "/" + id);
        var saved = parcels.findById(id).orElseThrow();
        assertThat(saved.getCustomerId()).isEqualTo(owner);
        assertThat(saved.getMerchantId()).isNull();
        assertThat(saved.getStatusSequence()).isEqualTo(1);
        assertThat(saved.getPickup().getContactPhone()).isEqualTo("+94771234567");
        assertThat(saved.getPickup().getDistrictName()).isEqualTo("Colombo");
        assertThat(saved.getDestinationZone().getCode()).isEqualTo("CENTRAL");
        String event = jdbc.queryForObject("SELECT payload::text FROM outbox_events WHERE "
                + "payload->'data'->>'parcelId'=?", String.class, id.toString());
        JsonNode envelope = json.readTree(event);
        assertThat(envelope.get("eventType").asText()).isEqualTo("parcel.created");
        assertThat(envelope.get("data").get("customerId").asText()).isEqualTo(owner.toString());
        assertThat(envelope.get("data").get("status").asText()).isEqualTo("CREATED");
        assertThat(event).doesNotContain("Private", "0771234567", "+94771234567",
                "0712345678", "private@example.invalid", "contactPhone", "contactName", "addressLine");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE parcel_id=?", Integer.class, id))
                .isEqualTo(1);
        mvc.perform(get(PATH + "/" + id).header("Authorization", TOKENS.customer().userId(owner).bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pickup.districtName").value("Colombo"))
                .andExpect(jsonPath("$.ownerType").value("CUSTOMER")).andExpect(jsonPath("$.history").doesNotExist())
                .andExpect(jsonPath("$.courier").doesNotExist()).andExpect(jsonPath("$.proof").doesNotExist());
    }

    @Test
    void AT08FeeChangeWritesNothingAndFailedKeyCanBeReused() throws Exception {
        price(900);
        create("fee", body()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEE_CHANGED")).andExpect(jsonPath("$.fee.total").value(900));
        assertThat(parcelCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE owner_id=?", Integer.class, owner))
                .isZero();
        price(800);
        create("fee", body()).andExpect(status().isCreated());
    }

    @Test
    void AT09ReplayAfterPriceChangeUsesOriginalResponseAndDoesNotCallPricing() throws Exception {
        String original = create("replay", body()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int pricingCalls = PRICING.getAllServeEvents().size();
        price(900);
        String replay = create("replay", body()).andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "true")).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(replay)).isEqualTo(json.readTree(original));
        assertThat(PRICING.getAllServeEvents()).hasSize(pricingCalls);
        create("replay", body().replace("Books", "Clothes")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(parcelCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"400,VALIDATION_ERROR", "422,ZONE_NOT_SERVICEABLE", "422,PRICING_NOT_CONFIGURED", "503,SERVICE_UNAVAILABLE"})
    void pricingErrorsPropagateWithoutPersistingAnything(int httpStatus, String code) throws Exception {
        PRICING.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/internal/v1/pricing/calculate")
                .willReturn(aResponse().withStatus(httpStatus).withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"code\":\"" + code + "\",\"detail\":\"Pricing rejected request\","
                                + "\"errors\":[{\"field\":\"weightKg\",\"message\":\"Invalid weight\"}]}")));
        create("pricing-error", body()).andExpect(status().is(httpStatus)).andExpect(jsonPath("$.code").value(code));
        assertThat(parcelCount()).isZero();
    }

    @Test
    void trackingCollisionRetriesAndExhaustionLeaveNoOrphans() throws Exception {
        parcels.saveAndFlush(parcel("SD-0000000001", foreign, null, null));
        when(tracking.generate()).thenReturn("SD-0000000001", "SD-0000000001", "SD-0000000002");
        create("collision", body()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber").value("SD-0000000002"));
        verify(tracking, times(3)).generate();
        clearInvocations(tracking);
        when(tracking.generate()).thenReturn("SD-0000000001");
        create("exhausted", body()).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        verify(tracking, times(5)).generate();
        assertThat(parcelCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE owner_id=?", Integer.class, owner))
                .isEqualTo(1);
    }

    @Test
    void outboxFailureRollsBackParcelAndSuccessfulKey() throws Exception {
        OutboxService target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(outbox);
        doThrow(new IllegalStateException("synthetic outbox failure")).when(target)
                .add(eq(EventTypes.PARCEL_CREATED), any(ParcelCreatedData.class));
        create("rollback", body()).andExpect(status().isInternalServerError());
        assertThat(parcelCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE owner_id=?", Integer.class, owner))
                .isZero();
    }

    @Test
    void concurrentActualCreationReplaysWinnerWithOneParcelKeyAndOutbox() throws Exception {
        var validated = json.readValue(body(), CustomerParcelCreationRequest.class);
        CyclicBarrier ready = new CyclicBarrier(2);
        var request = new CreationRequest(OwnerType.CUSTOMER, owner, validated.toParcelInput(),
                validated.expectedFee(), null, "race", keys.hashRequest(validated),
                () -> {
                    try { ready.await(30, TimeUnit.SECONDS); }
                    catch (Exception e) { throw new IllegalStateException(e); }
                    return BigDecimal.ZERO;
                }, CustomerParcelCreatedResponse::from);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<CreationResult> first = threads.submit(() -> creation.create(request));
            Future<CreationResult> second = threads.submit(() -> creation.create(request));
            var a = first.get(60, TimeUnit.SECONDS);
            var b = second.get(60, TimeUnit.SECONDS);
            assertThat(a.parcelId()).isEqualTo(b.parcelId());
            assertThat(a.replay()).isNotEqualTo(b.replay());
            assertThat(parcelCount()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE owner_id=?", Integer.class, owner)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE payload->'data'->>'parcelId'=?",
                    Integer.class, a.parcelId().toString())).isEqualTo(1);
        } finally {
            threads.shutdownNow();
            assertThat(threads.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void authenticationRolesClientTokensAndForeignDetailAreEnforced() throws Exception {
        UUID id = parcels.saveAndFlush(parcel(new TrackingNumberGenerator().generate(), foreign, null, null)).getId();
        for (String token : List.of(TOKENS.admin().bearer(), TOKENS.courier().bearer(),
                TOKENS.merchant(UUID.randomUUID()).bearer(), TOKENS.client(UUID.randomUUID(), "orders:read").bearer())) {
            mvc.perform(get(PATH).header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(get(PATH + "/" + id).header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(PATH)
                    .header("Authorization", token).contentType("application/json").content(body()))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH + "/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(PATH)
                .contentType("application/json").content(body())).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH + "/" + id).header("Authorization", TOKENS.customer().userId(owner).bearer()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void invalidBodiesHeadersAndOwnerInjectionAreRejected() throws Exception {
        create("bad key", body()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        create(null, body().replace("2.00", "2.001")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("parcel.weightKg"));
        var injected = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(body());
        injected.put("ownerId", foreign.toString());
        create(null, injected.toString()).andExpect(status().isBadRequest());
        assertThat(parcelCount()).isZero();
    }

    @Test
    void listFiltersInclusiveColomboDatesSortPaginationAndOwner() throws Exception {
        seed("SD-0000000010", owner, "2026-11-09T18:29:59Z", "CREATED");
        seed("SD-0000000011", owner, "2026-11-09T18:30:00Z", "CREATED");
        seed("SD-0000000012", owner, "2026-11-10T10:30:00Z", "CREATED");
        seed("SD-0000000013", owner, "2026-11-10T18:30:00Z", "DELIVERED");
        seed("SD-0000000014", foreign, "2026-11-10T10:30:00Z", "CREATED");
        String auth = TOKENS.customer().userId(owner).bearer();
        mvc.perform(get(PATH).header("Authorization", auth).param("from", "2026-11-10")
                .param("to", "2026-11-10").param("status", "CREATED").param("q", "SD-000000001")
                .param("size", "1").param("sort", "trackingNumber,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.items[0].trackingNumber").value("SD-0000000011"));
        mvc.perform(get(PATH).header("Authorization", auth).param("page", "1").param("size", "1")
                .param("from", "2026-11-10").param("to", "2026-11-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].trackingNumber").value("SD-0000000011"));
        mvc.perform(get(PATH).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.items[0].trackingNumber").value("SD-0000000013"));
        mvc.perform(get(PATH).header("Authorization", auth).param("q", "%")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
        for (String[] invalid : new String[][]{{"size","101"},{"size","0"},{"page","-1"},
                {"sort","ownerId,asc"},{"sort","status,invalid"},{"sort","status"},{"status","BAD"}}) {
            mvc.perform(get(PATH).header("Authorization", auth).param(invalid[0], invalid[1]))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get(PATH).header("Authorization", auth).param("from","2026-11-11").param("to","2026-11-10"))
                .andExpect(status().isBadRequest());
    }

    private void seed(String number, UUID customer, String instant, String status) {
        var p = parcels.saveAndFlush(parcel(number, customer, null, null));
        jdbc.update("UPDATE parcels SET created_at=?,status=? WHERE id=?", Timestamp.from(Instant.parse(instant)), status, p.getId());
    }

    @Test
    void abovePricingMaximumReachesPricingAndPreservesWeightValidationError() throws Exception {
        PRICING.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/internal/v1/pricing/calculate")
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"code\":\"VALIDATION_ERROR\",\"detail\":\"Weight exceeds configured maximum\","
                                + "\"errors\":[{\"field\":\"weightKg\",\"message\":\"Weight exceeds configured maximum\"}]}")));
        create("above-max", body().replace("2.00", "30.01")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("Weight exceeds configured maximum"))
                .andExpect(jsonPath("$.errors[0].field").value("weightKg"))
                .andExpect(jsonPath("$.errors[0].message").value("Weight exceeds configured maximum"));
        PRICING.verify(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/internal/v1/pricing/calculate"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath(
                        "$.weightKg", com.github.tomakehurst.wiremock.client.WireMock.equalTo("30.01"))));
        assertNoCommittedCreation();
    }

    @Test
    void successfulCreationWithoutKeyStoresOneParcelAndEventButNoKey() throws Exception {
        var response = create(null, body()).andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replay"))
                .andExpect(jsonPath("$.status").value("CREATED")).andReturn().getResponse();
        UUID id = UUID.fromString(json.readTree(response.getContentAsString()).get("id").asText());
        assertThat(response.getHeader("Location")).isEqualTo(PATH + "/" + id);
        assertThat(parcelCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE owner_id=?", Integer.class, owner))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type='parcel.created' "
                + "AND payload->'data'->>'parcelId'=?", Integer.class, id.toString())).isEqualTo(1);
    }

    @Test
    void failureAfterRealOutboxInsertRollsBackParcelKeyAndEvent() throws Exception {
        OutboxService target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(outbox);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("synthetic failure after outbox insert");
        }).when(target).add(eq(EventTypes.PARCEL_CREATED), any(ParcelCreatedData.class));
        create("rollback-all", body()).andExpect(status().isInternalServerError());
        assertNoCommittedCreation();
    }


    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"{}", "{\"reason\":\"Changed plans\"}"})
    @DisplayName("AT-25 FR-PAR-009: CREATED cancellation uses the real lifecycle")
    void AT25CancelsCreatedThroughRealLifecycle(String body) throws Exception {
        var saved = parcels.saveAndFlush(parcel(new TrackingNumberGenerator().generate(), owner, null, null));
        var request = post(PATH + "/" + saved.getId() + "/cancel")
                .header("Authorization", TOKENS.customer().userId(owner).bearer());
        if (body != null) request.contentType("application/json").content(body);
        mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.trackingNumber").value(saved.getTrackingNumber()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.statusChangedAt").isNotEmpty())
                .andExpect(jsonPath("$.sequence").doesNotExist());
        var cancelled = parcels.findById(saved.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(ParcelStatus.CANCELLED);
        assertThat(cancelled.getStatusSequence()).isEqualTo(2);
        assertThat(cancelled.getTerminalAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type='parcel.status.changed' "
                + "AND payload->'data'->>'parcelId'=?", Integer.class, saved.getId().toString())).isEqualTo(1);
    }

    @Test
    @DisplayName("TST-031 FR-PAR-009: post-pickup cancellation is 409 and a foreign owner gets 404")
    void cancellationAfterPickupAndForeignOwnershipAreRejected() throws Exception {
        var saved = parcels.saveAndFlush(parcel(new TrackingNumberGenerator().generate(), owner, null, null));
        jdbc.update("UPDATE parcels SET status='PICKED_UP' WHERE id=?", saved.getId());
        mvc.perform(post(PATH + "/" + saved.getId() + "/cancel")
                .header("Authorization", TOKENS.customer().userId(owner).bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        mvc.perform(post(PATH + "/" + saved.getId() + "/cancel")
                .header("Authorization", TOKENS.customer().userId(foreign).bearer()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(parcels.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(ParcelStatus.PICKED_UP);
    }

    @Test
    @DisplayName("TST-031 FR-PAR-009: cancellation requires a CUSTOMER user token")
    void cancellationRequiresCustomerUserToken() throws Exception {
        String path = PATH + "/" + UUID.randomUUID() + "/cancel";
        for (String token : List.of(TOKENS.admin().bearer(), TOKENS.courier().bearer(),
                TOKENS.merchant(UUID.randomUUID()).bearer(), TOKENS.client(UUID.randomUUID(), "orders:write").bearer())) {
            mvc.perform(post(path).header("Authorization", token)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        mvc.perform(post(path)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TST-021 FR-PAR-009: optional cancellation reason has a 300-character limit")
    void cancellationReasonAccepts300AndRejects301Characters() throws Exception {
        var saved = parcels.saveAndFlush(parcel(new TrackingNumberGenerator().generate(), owner, null, null));
        String path = PATH + "/" + saved.getId() + "/cancel";
        mvc.perform(post(path).header("Authorization", TOKENS.customer().userId(owner).bearer())
                .contentType("application/json").content(json.writeValueAsString(Map.of("reason", "a".repeat(301)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        assertThat(parcels.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(ParcelStatus.CREATED);
        mvc.perform(post(path).header("Authorization", TOKENS.customer().userId(owner).bearer())
                .contentType("application/json").content(json.writeValueAsString(Map.of("reason", "a".repeat(300)))))
                .andExpect(status().isOk());
    }

    private void assertNoCommittedCreation() {
        assertThat(parcelCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE owner_id=?", Integer.class, owner))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE payload->'data'->>'customerId'=?",
                Integer.class, owner.toString())).isZero();
    }
}

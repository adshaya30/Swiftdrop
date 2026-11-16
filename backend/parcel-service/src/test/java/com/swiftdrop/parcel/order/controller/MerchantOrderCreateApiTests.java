package com.swiftdrop.parcel.order.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.swiftdrop.parcel.support.ParcelTestConfiguration.TOKENS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.OutboxService;
import com.swiftdrop.parcel.order.AbstractMerchantOrderTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("TST-021")
@Tag("TST-032")
class MerchantOrderCreateApiTests extends AbstractMerchantOrderTest {

    @MockitoSpyBean OutboxService outbox;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("TST-021 AT-07 FR-PAR-007: an order is priced with the merchant discount and returns 201 with trackingUrl")
    void createsWithDiscount() throws Exception {
        var response = createOrder(TOKENS.client(mid, "orders:write").bearer(), null, orderBody("ORD-1001", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.merchantReference").value("ORD-1001"))
                .andExpect(jsonPath("$.fee.currency").value("LKR"))
                .andExpect(jsonPath("$.fee.baseFee").value(800.00))
                .andExpect(jsonPath("$.fee.weightFee").value(600.00))
                .andExpect(jsonPath("$.fee.sizeSurcharge").value(400.00))
                .andExpect(jsonPath("$.fee.discountAmount").value(180.00))
                .andExpect(jsonPath("$.fee.total").value(1620.00))
                .andReturn().getResponse();

        JsonNode body = json.readTree(response.getContentAsString());
        UUID id = UUID.fromString(body.get("id").asText());
        String trackingNumber = body.get("trackingNumber").asText();
        assertThat(trackingNumber).matches("^SD-[0-9A-HJKMNP-TV-Z]{10}$");
        assertThat(body.get("trackingUrl").asText()).isEqualTo(PUBLIC_BASE_URL + "/track/" + trackingNumber);
        assertThat(body.has("createdAt")).isTrue();
        assertThat(response.getHeader("Location")).isEqualTo(ORDERS + "/" + id);
        PRICING.verify(postRequestedFor(urlEqualTo("/internal/v1/pricing/calculate"))
                .withRequestBody(matchingJsonPath("$[?(@.discountPercent == 10.0)]")));

        var row = jdbc.queryForMap("SELECT merchant_id, customer_id, fee_total FROM parcels WHERE id = ?", id);
        assertThat(row.get("merchant_id")).isEqualTo(mid);
        assertThat(row.get("customer_id")).isNull();
        assertThat(row.get("fee_total").toString()).isEqualTo("1620.00");
    }

    @Test
    @DisplayName("TST-021 BR-28: the parcel.created outbox row names the merchant and carries no contact data")
    void outboxWithoutContactData() throws Exception {
        UUID id = newOrder(mid, "ORD-PRIV");

        String event = jdbc.queryForObject("SELECT payload::text FROM outbox_events WHERE "
                + "payload->'data'->>'parcelId' = ?", String.class, id.toString());
        JsonNode envelope = json.readTree(event);
        assertThat(envelope.get("eventType").asText()).isEqualTo(EventTypes.PARCEL_CREATED);
        assertThat(envelope.at("/data/ownerType").asText()).isEqualTo("MERCHANT");
        assertThat(envelope.at("/data/merchantId").asText()).isEqualTo(mid.toString());
        assertThat(envelope.at("/data/merchantReference").asText()).isEqualTo("ORD-PRIV");
        assertThat(event).doesNotContain("Store Sender", "Buyer Person", "0771234567", "+94771234567",
                "0712345678", "buyer@example.invalid", "warehouse", "home lane", "contactPhone", "addressLine");
    }

    @Test
    @DisplayName("TST-021 FR-PAR-007: expectedFee is optional, and compared only when present")
    void expectedFeeOptional() throws Exception {
        createOrder(TOKENS.merchant(mid).bearer(), null, orderBody(null, null)).andExpect(status().isCreated());
        createOrder(TOKENS.merchant(mid).bearer(), null, orderBody(null, "1620.00")).andExpect(status().isCreated());
        createOrder(TOKENS.merchant(mid).bearer(), null, orderBody(null, "1500.00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEE_CHANGED"))
                .andExpect(jsonPath("$.fee.total").value(1620.00));

        assertThat(orderCount(mid)).isEqualTo(2);
    }

    @Test
    @DisplayName("TST-021 BR-22: a merchantReference in use is 409; after CANCELLED or FAILED it is free again")
    void duplicateReference() throws Exception {
        UUID first = newOrder(mid, "ORD-DUP");
        createOrder(TOKENS.merchant(mid).bearer(), null, orderBody("ORD-DUP", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_MERCHANT_REFERENCE"));

        // Another merchant may use the same reference.
        createOrder(TOKENS.merchant(otherMid).bearer(), null, orderBody("ORD-DUP", null))
                .andExpect(status().isCreated());

        setStatus(first, ParcelStatus.CANCELLED);
        UUID second = newOrder(mid, "ORD-DUP");
        setStatus(second, ParcelStatus.FAILED);
        createOrder(TOKENS.merchant(mid).bearer(), null, orderBody("ORD-DUP", null)).andExpect(status().isCreated());
        assertThat(orderCount(mid)).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "REJECTED", "SUSPENDED"})
    @DisplayName("TST-021 TST-032 BR-23: a merchant that is not APPROVED gets 403 MERCHANT_NOT_ACTIVE and nothing is priced")
    void merchantNotApproved(String status) throws Exception {
        merchant(mid, status, "ACTIVE", "10.00");

        createOrder(TOKENS.client(mid, "orders:write").bearer(), null, orderBody(null, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_ACTIVE"));

        PRICING.verify(0, postRequestedFor(urlEqualTo("/internal/v1/pricing/calculate")));
        assertThat(orderCount(mid)).isZero();
    }

    @Test
    @DisplayName("TST-021 BR-23: an APPROVED merchant whose user is DEACTIVATED gets MERCHANT_NOT_ACTIVE")
    void userDeactivated() throws Exception {
        merchant(mid, "APPROVED", "DEACTIVATED", "10.00");

        createOrder(TOKENS.merchant(mid).bearer(), null, orderBody(null, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("TST-021 FR-PAR-006: the same key and body replays the original 201 without a second order or re-check")
    void idempotentReplay() throws Exception {
        String bearer = TOKENS.client(mid, "orders:write").bearer();
        String first = createOrder(bearer, "merchant-key-1", orderBody("ORD-IDEM", null))
                .andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotent-Replay"))
                .andReturn().getResponse().getContentAsString();

        // A later suspension or price change does not matter for a replay (idempotency lookup comes first).
        merchant(mid, "SUSPENDED", "ACTIVE", "10.00");
        String replay = createOrder(bearer, "merchant-key-1", orderBody("ORD-IDEM", null))
                .andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replay", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(replay)).isEqualTo(json.readTree(first));
        assertThat(orderCount(mid)).isEqualTo(1);
        AUTH.verify(1, getRequestedFor(urlEqualTo("/internal/v1/merchants/" + mid)));
    }

    @Test
    @DisplayName("TST-021 FR-PAR-006: the same key with another body gives 422 IDEMPOTENCY_KEY_REUSED")
    void keyReused() throws Exception {
        String bearer = TOKENS.merchant(mid).bearer();
        createOrder(bearer, "merchant-key-2", orderBody("ORD-A", null)).andExpect(status().isCreated());

        createOrder(bearer, "merchant-key-2", orderBody("ORD-B", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(orderCount(mid)).isEqualTo(1);
    }

    @Test
    @DisplayName("TST-021 FR-PAR-006: keys are scoped per merchant, so another merchant may use the same key")
    void keyScopedPerMerchant() throws Exception {
        createOrder(TOKENS.merchant(mid).bearer(), "shared-key", orderBody("ORD-S", null))
                .andExpect(status().isCreated());
        createOrder(TOKENS.merchant(otherMid).bearer(), "shared-key", orderBody("ORD-S", null))
                .andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotent-Replay"));
    }

    @Test
    @DisplayName("TST-021 FR-PAR-006: a failure after the parcel insert leaves no parcel, key or outbox row")
    void atomic() throws Exception {
        // Stubbing passes through the spied bean's transactional proxy (MANDATORY), so it needs a transaction.
        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                doThrow(new IllegalStateException("outbox failure")).when(outbox)
                        .add(eq(EventTypes.PARCEL_CREATED), any()));

        createOrder(TOKENS.merchant(mid).bearer(), "atomic-key", orderBody("ORD-ATOM", null))
                .andExpect(status().isInternalServerError());

        assertThat(orderCount(mid)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE owner_id = ?", Integer.class,
                mid)).isZero();
    }

    @Test
    @DisplayName("AT-14 FR-PRC-004: an invalid district from pricing-service is returned unchanged as 400")
    void invalidDistrict() throws Exception {
        PRICING.stubFor(post("/internal/v1/pricing/calculate").willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"status\":400,\"code\":\"VALIDATION_ERROR\",\"detail\":\"Unknown district\"}")));

        createOrder(TOKENS.merchant(mid).bearer(), null, orderBody(null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(orderCount(mid)).isZero();
    }

    @Test
    @DisplayName("FR-PAR-007: merchantReference longer than 64 characters and unknown fields are rejected")
    void validation() throws Exception {
        createOrder(TOKENS.merchant(mid).bearer(), null, orderBody("R".repeat(65), null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        createOrder(TOKENS.merchant(mid).bearer(), null,
                orderBody(null, null).replaceFirst("\\{", "{\"merchantId\":\"" + otherMid + "\","))
                .andExpect(status().isBadRequest());
        assertThat(orderCount(mid)).isZero();
        assertThat(orderCount(otherMid)).isZero();
    }

    @Test
    @DisplayName("TST-032: a client token with only orders:read cannot create an order")
    void readScopeCannotCreate() throws Exception {
        createOrder(TOKENS.client(mid, "orders:read").bearer(), null, orderBody(null, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(orderCount(mid)).isZero();
    }

    @Test
    @DisplayName("TST-032 BR-25: other roles get 403, no token gets 401")
    void otherRoles() throws Exception {
        createOrder(TOKENS.customer().bearer(), null, orderBody(null, null)).andExpect(status().isForbidden());
        createOrder(TOKENS.admin().bearer(), null, orderBody(null, null)).andExpect(status().isForbidden());
        createOrder(TOKENS.courier().bearer(), null, orderBody(null, null)).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(ORDERS)
                        .contentType("application/json").content(orderBody(null, null)))
                .andExpect(status().isUnauthorized());
    }
}

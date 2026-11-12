package com.swiftdrop.parcel.order;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.swiftdrop.parcel.support.ParcelTestConfiguration.TOKENS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.parcel.support.AbstractParcelDatabaseTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Real PostgreSQL; WireMock stubs of the SRS 7.4 auth-service and pricing-service contracts. */
public abstract class AbstractMerchantOrderTest extends AbstractParcelDatabaseTest {

    protected static final WireMockServer AUTH = new WireMockServer(options().dynamicPort());
    protected static final WireMockServer PRICING = new WireMockServer(options().dynamicPort());
    protected static final String ORDERS = "/api/v1/merchant/orders";
    protected static final String PUBLIC_BASE_URL = "https://swiftdrop.test";

    static {
        AUTH.start();
        PRICING.start();
    }

    @DynamicPropertySource
    static void dependencies(DynamicPropertyRegistry registry) {
        registry.add("parcel.auth-service-url", AUTH::baseUrl);
        registry.add("parcel.pricing-service-url", PRICING::baseUrl);
        registry.add("parcel.public-base-url", () -> PUBLIC_BASE_URL);
    }

    @AfterAll
    static void stopStubs() {
        // Kept running for the whole JVM: cached Spring contexts point at these ports.
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;

    protected final UUID mid = UUID.randomUUID();
    protected final UUID otherMid = UUID.randomUUID();

    @BeforeEach
    void resetStubs() {
        AUTH.resetAll();
        PRICING.resetAll();
        merchant(mid, "APPROVED", "ACTIVE", "10.00");
        merchant(otherMid, "APPROVED", "ACTIVE", "0.00");
        priceAt07();
    }

    @AfterEach
    void deleteRows() {
        for (UUID owner : new UUID[] {mid, otherMid}) {
            jdbc.update("DELETE FROM outbox_events WHERE payload->'data'->>'parcelId' IN "
                    + "(SELECT id::text FROM parcels WHERE merchant_id = ?)", owner);
            jdbc.update("DELETE FROM idempotency_keys WHERE owner_id = ?", owner);
            jdbc.update("DELETE FROM parcels WHERE merchant_id = ?", owner);
        }
    }

    protected static void merchant(UUID id, String status, String userStatus, String discount) {
        AUTH.stubFor(get("/internal/v1/merchants/" + id).willReturn(okJson("""
                {"id":"%s","userId":"%s","businessName":"Test Store","status":"%s","userStatus":"%s",
                 "discountPercent":%s,"contactEmail":"store@example.invalid"}
                """.formatted(id, UUID.randomUUID(), status, userStatus, discount))));
    }

    /** AT-07: Colombo to Jaffna, 5 kg, LARGE, 10 % discount: 800 + 600 + 400 = 1,800.00, discount 180.00, total 1,620.00. */
    protected static void priceAt07() {
        PRICING.stubFor(post("/internal/v1/pricing/calculate").willReturn(okJson("""
                {"currency":"LKR","originZone":{"code":"WESTERN","name":"Western"},
                 "destinationZone":{"code":"NORTH_EAST","name":"Northern & Eastern"},
                 "baseFee":800.00,"weightFee":600.00,"sizeSurcharge":400.00,"discountAmount":180.00,
                 "total":1620.00,"originDistrict":{"code":"COLOMBO","name":"Colombo"},
                 "destinationDistrict":{"code":"JAFFNA","name":"Jaffna"},"subtotal":1800.00,
                 "discountPercent":10.00}
                """)));
    }

    protected static String orderBody(String reference, String expectedFee) {
        return """
                {%s"pickup":{"contactName":"Store Sender","contactPhone":"0771234567",
                 "addressLine1":"Store warehouse road","city":"Colombo 03","districtCode":"COLOMBO"},
                 "dropoff":{"contactName":"Buyer Person","contactPhone":"0712345678",
                 "addressLine1":"Buyer home lane","city":"Jaffna","districtCode":"JAFFNA",
                 "contactEmail":"buyer@example.invalid"},
                 "parcel":{"weightKg":5.00,"size":"LARGE","description":"Shoes"}%s}
                """.formatted(reference == null ? "" : "\"merchantReference\":\"" + reference + "\",",
                expectedFee == null ? "" : ",\"expectedFee\":" + expectedFee);
    }

    protected ResultActions createOrder(String bearer, String key, String body) throws Exception {
        var request = MockMvcRequestBuilders.post(ORDERS).header("Authorization", bearer)
                .contentType("application/json").content(body);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request);
    }

    /** Creates an order as the merchant's dashboard user and returns its id. */
    protected UUID newOrder(UUID merchant, String reference) throws Exception {
        String response = createOrder(TOKENS.merchant(merchant).bearer(), null, orderBody(reference, null))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    protected void setStatus(UUID parcelId, ParcelStatus status) {
        jdbc.update("UPDATE parcels SET status = ?, terminal_at = CASE WHEN ? THEN now() ELSE NULL END WHERE id = ?",
                status.name(), status.isTerminal(), parcelId);
    }

    protected int orderCount(UUID merchant) {
        return jdbc.queryForObject("SELECT count(*) FROM parcels WHERE merchant_id = ?", Integer.class, merchant);
    }
}

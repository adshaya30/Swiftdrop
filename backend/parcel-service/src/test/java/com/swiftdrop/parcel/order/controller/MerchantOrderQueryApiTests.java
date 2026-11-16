package com.swiftdrop.parcel.order.controller;

import static com.swiftdrop.parcel.support.ParcelTestConfiguration.TOKENS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.parcel.order.AbstractMerchantOrderTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

@Tag("TST-032")
class MerchantOrderQueryApiTests extends AbstractMerchantOrderTest {

    @Test
    @DisplayName("FR-PAR-008: a merchant lists only its own orders, with pagination metadata")
    void listsOwnOrders() throws Exception {
        newOrder(mid, "ORD-L1");
        newOrder(mid, "ORD-L2");
        newOrder(otherMid, "ORD-OTHER");

        list(TOKENS.merchant(mid).bearer(), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[0].ownerType").value("MERCHANT"))
                .andExpect(jsonPath("$.items[?(@.merchantReference == 'ORD-OTHER')]").isEmpty());
    }

    @Test
    @DisplayName("FR-PAR-008: filters status, merchantReference, q prefix and an inclusive Colombo date range")
    void filters() throws Exception {
        UUID cancelled = newOrder(mid, "ORD-F1");
        newOrder(mid, "ORD-F2");
        setStatus(cancelled, ParcelStatus.CANCELLED);
        String tracking = jdbc.queryForObject("SELECT tracking_number FROM parcels WHERE id = ?", String.class,
                cancelled);
        String bearer = TOKENS.merchant(mid).bearer();

        list(bearer, "status=CANCELLED").andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].merchantReference").value("ORD-F1"));
        list(bearer, "merchantReference=ORD-F2").andExpect(jsonPath("$.totalItems").value(1));
        list(bearer, "q=" + tracking.substring(0, 8)).andExpect(jsonPath("$.items[0].id").value(cancelled.toString()));
        list(bearer, "from=2000-01-01&to=2000-01-02").andExpect(jsonPath("$.totalItems").value(0));
        list(bearer, "from=2000-01-01").andExpect(jsonPath("$.totalItems").value(2));
        list(bearer, "size=1&sort=trackingNumber,asc").andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("BR-26 FR-PAR-009: invalid pagination, sort or date range gives 400 VALIDATION_ERROR")
    void invalidParameters() throws Exception {
        String bearer = TOKENS.merchant(mid).bearer();
        list(bearer, "size=101").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        list(bearer, "sort=fee,asc").andExpect(status().isBadRequest());
        list(bearer, "from=2026-11-02&to=2026-11-01").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TST-032: orders:read lists and reads; orders:write alone cannot")
    void readScope() throws Exception {
        UUID id = newOrder(mid, "ORD-S1");

        list(TOKENS.client(mid, "orders:read").bearer(), "").andExpect(status().isOk());
        detail(TOKENS.client(mid, "orders:read").bearer(), id).andExpect(status().isOk());
        list(TOKENS.client(mid, "orders:write").bearer(), "").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        detail(TOKENS.client(mid, "orders:write").bearer(), id).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-PAR-008: the detail returns the order; another merchant's or an unknown order is 404")
    void detailOwnership() throws Exception {
        UUID id = newOrder(mid, "ORD-D1");

        detail(TOKENS.merchant(mid).bearer(), id).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.merchantReference").value("ORD-D1"))
                .andExpect(jsonPath("$.fee.total").value(1620.00))
                .andExpect(jsonPath("$.dropoff.districtName").value("Jaffna"));
        detail(TOKENS.merchant(otherMid).bearer(), id).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        detail(TOKENS.merchant(mid).bearer(), UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-PAR-008 BR-07: a CREATED order is cancelled with 200 and a parcel.status.changed outbox row")
    void cancels() throws Exception {
        UUID id = newOrder(mid, "ORD-C1");

        cancel(TOKENS.client(mid, "orders:write").bearer(), id, "{\"reason\":\"Customer changed mind\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.trackingNumber").exists())
                .andExpect(jsonPath("$.statusChangedAt").exists());

        assertThat(statusOf(id)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type = "
                + "'parcel.status.changed' AND payload->'data'->>'parcelId' = ?", Integer.class, id.toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("FR-PAR-008: the reason is optional")
    void cancelWithoutBody() throws Exception {
        UUID id = newOrder(mid, "ORD-C2");

        mvc.perform(post(ORDERS + "/" + id + "/cancel").header("Authorization", TOKENS.merchant(mid).bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("BR-07: cancelling after pickup gives 409 INVALID_STATUS_TRANSITION")
    void cancelAfterPickup() throws Exception {
        UUID id = newOrder(mid, "ORD-C3");
        setStatus(id, ParcelStatus.PICKED_UP);

        cancel(TOKENS.merchant(mid).bearer(), id, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        assertThat(statusOf(id)).isEqualTo("PICKED_UP");
    }

    @Test
    @DisplayName("TST-032 BR-25: a merchant cannot cancel another merchant's order (404)")
    void cancelForeign() throws Exception {
        UUID id = newOrder(mid, "ORD-C4");

        cancel(TOKENS.merchant(otherMid).bearer(), id, null).andExpect(status().isNotFound());
        assertThat(statusOf(id)).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("TST-032 BR-23: a suspended merchant cannot cancel; the re-check happens at cancel time")
    void cancelSuspended() throws Exception {
        UUID id = newOrder(mid, "ORD-C5");
        merchant(mid, "SUSPENDED", "ACTIVE", "10.00");

        cancel(TOKENS.merchant(mid).bearer(), id, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_ACTIVE"));
        assertThat(statusOf(id)).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("TST-032: orders:read alone cannot cancel; a reason over 300 characters is 400")
    void cancelScopeAndValidation() throws Exception {
        UUID id = newOrder(mid, "ORD-C6");

        cancel(TOKENS.client(mid, "orders:read").bearer(), id, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        cancel(TOKENS.merchant(mid).bearer(), id, "{\"reason\":\"" + "x".repeat(301) + "\"}")
                .andExpect(status().isBadRequest());
        assertThat(statusOf(id)).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("TST-032 TST-033: other roles get 403 and no token gets 401 on list, detail and cancel")
    void otherRoles() throws Exception {
        UUID id = newOrder(mid, "ORD-R1");

        for (String bearer : new String[] {TOKENS.customer().bearer(), TOKENS.admin().bearer(),
                TOKENS.courier().bearer()}) {
            list(bearer, "").andExpect(status().isForbidden());
            detail(bearer, id).andExpect(status().isForbidden());
            cancel(bearer, id, null).andExpect(status().isForbidden());
        }
        mvc.perform(get(ORDERS)).andExpect(status().isUnauthorized());
        mvc.perform(get(ORDERS + "/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post(ORDERS + "/" + id + "/cancel")).andExpect(status().isUnauthorized());
    }

    private ResultActions list(String bearer, String query) throws Exception {
        return mvc.perform(get(ORDERS + (query.isEmpty() ? "" : "?" + query)).header("Authorization", bearer));
    }

    private ResultActions detail(String bearer, UUID id) throws Exception {
        return mvc.perform(get(ORDERS + "/" + id).header("Authorization", bearer));
    }

    private ResultActions cancel(String bearer, UUID id, String body) throws Exception {
        var request = post(ORDERS + "/" + id + "/cancel").header("Authorization", bearer);
        if (body != null) {
            request.contentType("application/json").content(body);
        }
        return mvc.perform(request);
    }

    private String statusOf(UUID id) {
        return jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, id);
    }
}

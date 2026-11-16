package com.swiftdrop.parcel.lifecycle.controller;

import static com.swiftdrop.parcel.support.ParcelTestConfiguration.TOKENS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelRepository;
import com.swiftdrop.parcel.support.AbstractParcelDatabaseTest;
import com.swiftdrop.parcel.support.ParcelFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** FR-PAR-011, FR-PAR-012 (TST-029 parcel parts) and authorisation of the admin endpoints (TST-033). */
@Tag("TST-029")
class AdminParcelApiTests extends AbstractParcelDatabaseTest {

    private static final String BASE = "/api/v1/admin/parcels";
    private static final String NOTE = "Courier bike broke down, parcel recovered from the roadside";

    @Autowired MockMvc mvc;
    @Autowired ParcelRepository parcels;
    @Autowired JdbcTemplate jdbc;

    private final List<UUID> created = new ArrayList<>();
    private final UUID customerId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        for (UUID id : created) {
            jdbc.update("DELETE FROM outbox_events WHERE payload::text LIKE ?", "%" + id + "%");
            jdbc.update("DELETE FROM parcels WHERE id = ?", id);
        }
        created.clear();
    }

    private Parcel newParcel(UUID customer, UUID merchant, String reference, ParcelStatus status) {
        String tracking = "SD" + ThreadLocalRandom.current().nextLong(10_000_000_000L, 99_999_999_999L);
        Parcel saved = parcels.saveAndFlush(ParcelFixtures.parcel(tracking, customer, merchant, reference));
        created.add(saved.getId());
        if (status != ParcelStatus.CREATED) {
            jdbc.update("UPDATE parcels SET status = ? WHERE id = ?", status.name(), saved.getId());
        }
        return saved;
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String bearer, String body) throws Exception {
        if (bearer != null) {
            request.header("Authorization", bearer);
        }
        if (body != null) {
            request.contentType("application/json").content(body);
        }
        return mvc.perform(request);
    }

    private int outboxRows(UUID parcelId, String routingKey) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE routing_key = ? AND payload::text LIKE ?",
                Integer.class, routingKey, "%" + parcelId + "%");
    }

    // ---------------------------------------------------------------- FR-PAR-011 search and detail

    @Test
    @DisplayName("TST-029 FR-PAR-011: admin searches parcels of every owner with filters and pagination")
    void searchAndFilters() throws Exception {
        Parcel customerParcel = newParcel(customerId, null, null, ParcelStatus.CREATED);
        Parcel merchantParcel = newParcel(null, merchantId, "ORD-ADM-1", ParcelStatus.IN_TRANSIT);
        String bearer = TOKENS.admin().bearer();

        send(get(BASE + "?size=100"), bearer, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + customerParcel.getId() + "')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.id == '" + merchantParcel.getId() + "')]").isNotEmpty());
        send(get(BASE + "?status=IN_TRANSIT&ownerType=MERCHANT&q=ORD-ADM"), bearer, null)
                .andExpect(jsonPath("$.items[?(@.id == '" + merchantParcel.getId() + "')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.id == '" + customerParcel.getId() + "')]").isEmpty());
        send(get(BASE + "?ownerType=CUSTOMER&q=" + customerParcel.getTrackingNumber().substring(0, 8)), bearer, null)
                .andExpect(jsonPath("$.items[?(@.id == '" + customerParcel.getId() + "')]").isNotEmpty());
        send(get(BASE + "?originDistrictCode=COLOMBO&destinationDistrictCode=KANDY&size=1&sort=trackingNumber,asc"),
                bearer, null).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.size").value(1));
        send(get(BASE + "?originDistrictCode=NOWHERE"), bearer, null).andExpect(jsonPath("$.totalItems").value(0));
        send(get(BASE + "?from=2000-01-01&to=2000-01-02"), bearer, null).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    @DisplayName("TST-029 FR-PAR-011: a wildcard in q is a literal, bad page size, sort or dates give 400")
    void invalidParameters() throws Exception {
        newParcel(null, merchantId, "ORD-LIT-1", ParcelStatus.CREATED);
        String bearer = TOKENS.admin().bearer();

        send(get(BASE + "?q=%25"), bearer, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
        send(get(BASE + "?size=101"), bearer, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        send(get(BASE + "?sort=feeTotal,asc"), bearer, null).andExpect(status().isBadRequest());
        send(get(BASE + "?from=2026-11-02&to=2026-11-01"), bearer, null).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TST-029 FR-PAR-011: detail shows contacts, fee and both owner ids; unknown id gives 404")
    void detail() throws Exception {
        Parcel parcel = newParcel(null, merchantId, "ORD-DET-1", ParcelStatus.CREATED);
        String bearer = TOKENS.admin().bearer();

        send(get(BASE + "/" + parcel.getId()), bearer, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(parcel.getId().toString()))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.merchantReference").value("ORD-DET-1"))
                .andExpect(jsonPath("$.pickup.contactName").value("Test sender"))
                .andExpect(jsonPath("$.fee.total").value(800.00));
        send(get(BASE + "/" + UUID.randomUUID()), bearer, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---------------------------------------------------------------- cancel (FR-PAR-011, BR-07)

    @Test
    @DisplayName("TST-029 FR-PAR-011 BR-07: admin cancels a CREATED parcel with a reason; status event and audit written")
    void cancelCreated() throws Exception {
        Parcel parcel = newParcel(customerId, null, null, ParcelStatus.CREATED);
        String reason = "Customer called to cancel the booking";

        send(post(BASE + "/" + parcel.getId() + "/cancel"), TOKENS.admin().bearer(),
                "{\"reason\":\"" + reason + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.trackingNumber").value(parcel.getTrackingNumber()));

        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcel.getId()))
                .isEqualTo("CANCELLED");
        assertThat(outboxRows(parcel.getId(), "parcel.status.changed")).isEqualTo(1);
        assertThat(outboxRows(parcel.getId(), "audit.recorded")).isEqualTo(1);
    }

    @Test
    @DisplayName("TST-029 BR-07: cancel needs a reason of 10-300 characters (400) and only works from CREATED (409)")
    void cancelRules() throws Exception {
        Parcel created = newParcel(customerId, null, null, ParcelStatus.CREATED);
        Parcel inTransit = newParcel(customerId, null, null, ParcelStatus.IN_TRANSIT);
        String bearer = TOKENS.admin().bearer();

        send(post(BASE + "/" + created.getId() + "/cancel"), bearer, "{\"reason\":\"too short\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        send(post(BASE + "/" + created.getId() + "/cancel"), bearer, "{}").andExpect(status().isBadRequest());
        send(post(BASE + "/" + inTransit.getId() + "/cancel"), bearer,
                "{\"reason\":\"Cancel after pickup is not allowed\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        send(post(BASE + "/" + UUID.randomUUID() + "/cancel"), bearer,
                "{\"reason\":\"Parcel does not exist at all\"}").andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- force-fail (FR-PAR-012)

    @Test
    @DisplayName("TST-029 FR-PAR-012 AT-38: admin force-fails a stuck parcel; event emitted, audit has no note")
    void forceFail() throws Exception {
        Parcel parcel = newParcel(null, merchantId, "ORD-FAIL-1", ParcelStatus.OUT_FOR_DELIVERY);

        send(post(BASE + "/" + parcel.getId() + "/fail"), TOKENS.admin().bearer(),
                "{\"failureReason\":\"PARCEL_DAMAGED\",\"note\":\"" + NOTE + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));

        assertThat(outboxRows(parcel.getId(), "parcel.status.changed")).isEqualTo(1);
        List<String> audits = jdbc.queryForList(
                "SELECT payload::text FROM outbox_events WHERE routing_key = 'audit.recorded' AND payload::text LIKE ?",
                String.class, "%" + parcel.getId() + "%");
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0)).doesNotContain(NOTE).contains("PARCEL_FAILED_BY_ADMIN");
        assertThat(jdbc.queryForObject("SELECT terminal_at IS NOT NULL FROM parcels WHERE id = ?", Boolean.class,
                parcel.getId())).isTrue();
    }

    @Test
    @DisplayName("TST-029 FR-PAR-012: fail needs a note, an admin-override reason and a stage after pickup")
    void forceFailRules() throws Exception {
        Parcel stuck = newParcel(customerId, null, null, ParcelStatus.IN_TRANSIT);
        Parcel created = newParcel(customerId, null, null, ParcelStatus.CREATED);
        String bearer = TOKENS.admin().bearer();

        send(post(BASE + "/" + stuck.getId() + "/fail"), bearer, "{\"failureReason\":\"OTHER\"}")
                .andExpect(status().isBadRequest());
        send(post(BASE + "/" + stuck.getId() + "/fail"), bearer,
                "{\"failureReason\":\"OTHER\",\"note\":\"abc\"}").andExpect(status().isBadRequest());
        send(post(BASE + "/" + stuck.getId() + "/fail"), bearer,
                "{\"failureReason\":\"RECIPIENT_REFUSED\",\"note\":\"" + NOTE + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("failureReason"));
        send(post(BASE + "/" + created.getId() + "/fail"), bearer,
                "{\"failureReason\":\"OTHER\",\"note\":\"" + NOTE + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, stuck.getId()))
                .isEqualTo("IN_TRANSIT");
    }

    // ---------------------------------------------------------------- TST-033 authorisation

    @Test
    @Tag("TST-033")
    @DisplayName("TST-033 BR-25: every admin parcel endpoint gives 401 without a token and 403 to other roles and client tokens")
    void authorisation() throws Exception {
        Parcel parcel = newParcel(null, merchantId, "ORD-AUTH-1", ParcelStatus.IN_TRANSIT);
        String id = parcel.getId().toString();
        String cancelBody = "{\"reason\":\"Authorisation test reason\"}";
        String failBody = "{\"failureReason\":\"OTHER\",\"note\":\"Authorisation test note\"}";
        List<String> bearers = List.of(TOKENS.customer().bearer(), TOKENS.merchant(merchantId).bearer(),
                TOKENS.courier().bearer(), TOKENS.client(merchantId, "orders:read", "orders:write").bearer());

        send(get(BASE), null, null).andExpect(status().isUnauthorized());
        send(get(BASE + "/" + id), null, null).andExpect(status().isUnauthorized());
        send(post(BASE + "/" + id + "/cancel"), null, cancelBody).andExpect(status().isUnauthorized());
        send(post(BASE + "/" + id + "/fail"), null, failBody).andExpect(status().isUnauthorized());
        for (String bearer : bearers) {
            send(get(BASE), bearer, null).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            send(get(BASE + "/" + id), bearer, null).andExpect(status().isForbidden());
            send(post(BASE + "/" + id + "/cancel"), bearer, cancelBody).andExpect(status().isForbidden());
            send(post(BASE + "/" + id + "/fail"), bearer, failBody).andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcel.getId()))
                .isEqualTo("IN_TRANSIT");
    }
}

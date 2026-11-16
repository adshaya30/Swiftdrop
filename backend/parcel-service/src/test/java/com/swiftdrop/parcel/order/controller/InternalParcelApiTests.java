package com.swiftdrop.parcel.order.controller;

import static com.swiftdrop.parcel.support.ParcelTestConfiguration.TOKENS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftdrop.parcel.create.service.TrackingNumberGenerator;
import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelRepository;
import com.swiftdrop.parcel.support.AbstractParcelDatabaseTest;
import com.swiftdrop.parcel.support.ParcelFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Tag("TST-035")
class InternalParcelApiTests extends AbstractParcelDatabaseTest {

    @Autowired MockMvc mvc;
    @Autowired ParcelRepository parcels;
    @Autowired JdbcTemplate jdbc;
    @Value("${INTERNAL_API_KEY}") String internalKey;

    private final UUID customer = UUID.randomUUID();
    private final UUID courier = UUID.randomUUID();
    private final List<UUID> created = new ArrayList<>();

    @AfterEach
    void cleanup() {
        created.forEach(id -> {
            jdbc.update("DELETE FROM outbox_events WHERE payload->'data'->>'parcelId' = ?", id.toString());
            jdbc.update("DELETE FROM parcels WHERE id = ?", id);
        });
    }

    @Test
    @DisplayName("FR-PAR-016 SRS 7.4: the detail has the admin fields plus statusSequence and updatedAt")
    void detail() throws Exception {
        Parcel parcel = save();

        internal(get("/internal/v1/parcels/" + parcel.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(parcel.getId().toString()))
                .andExpect(jsonPath("$.trackingNumber").value(parcel.getTrackingNumber()))
                .andExpect(jsonPath("$.statusSequence").value(1))
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.customerId").value(customer.toString()))
                .andExpect(jsonPath("$.dropoff.contactName").value("Test recipient"))
                .andExpect(jsonPath("$.fee.total").value(800.00));
    }

    @Test
    @DisplayName("FR-NTF-007 SRS 7.4: the recipient endpoint returns {name, phone, email} of the drop-off contact")
    void recipient() throws Exception {
        Parcel parcel = save();

        internal(get("/internal/v1/parcels/" + parcel.getId() + "/recipient")).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test recipient"))
                .andExpect(jsonPath("$.phone").value("+94770000002"))
                .andExpect(jsonPath("$.email").value("test@example.invalid"))
                .andExpect(jsonPath("$.addressLine1").doesNotExist());
    }

    @Test
    @DisplayName("FR-PAR-016: a courier transition answers {parcelId, status, sequence, statusChangedAt} and writes the event")
    void transition() throws Exception {
        Parcel parcel = save();

        transition(parcel.getId(), body("PICKED_UP", "COURIER", null, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelId").value(parcel.getId().toString()))
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.sequence").value(2))
                .andExpect(jsonPath("$.statusChangedAt").exists());

        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcel.getId()))
                .isEqualTo("PICKED_UP");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type = "
                + "'parcel.status.changed' AND payload->'data'->>'parcelId' = ?", Integer.class,
                parcel.getId().toString())).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-PAR-016: DELIVERED with a recipient name, by courier, from OUT_FOR_DELIVERY")
    void delivered() throws Exception {
        Parcel parcel = save();
        jdbc.update("UPDATE parcels SET status = 'OUT_FOR_DELIVERY', status_sequence = 4 WHERE id = ?",
                parcel.getId());

        transition(parcel.getId(), body("DELIVERED", "COURIER", null, "Kamala Silva")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.sequence").value(5));
        assertThat(jdbc.queryForObject("SELECT terminal_at IS NOT NULL FROM parcels WHERE id = ?", Boolean.class,
                parcel.getId())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MERCHANT", "SYSTEM"})
    @DisplayName("FR-PAR-016: actorRole other than COURIER or ADMIN gives 400 VALIDATION_ERROR")
    void actorRoleValidated(String role) throws Exception {
        Parcel parcel = save();

        transition(parcel.getId(), body("PICKED_UP", role, null, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("actorRole"));
        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcel.getId()))
                .isEqualTo("CREATED");
    }

    @Test
    @DisplayName("FR-PAR-016: missing toStatus or an unknown actorRole is 400")
    void bodyValidated() throws Exception {
        Parcel parcel = save();

        transition(parcel.getId(), "{\"actorUserId\":\"" + courier + "\",\"actorRole\":\"COURIER\"}")
                .andExpect(status().isBadRequest());
        transition(parcel.getId(), body("PICKED_UP", "DISPATCHER", null, null)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SRS 7.2: business errors keep their codes: invalid transition 409, missing failure reason 400")
    void businessErrors() throws Exception {
        Parcel parcel = save();

        transition(parcel.getId(), body("DELIVERED", "COURIER", null, "Someone")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        transition(parcel.getId(), body("FAILED", "COURIER", null, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("FR-PAR-016: an unknown parcel is 404 on all three endpoints")
    void unknownParcel() throws Exception {
        UUID unknown = UUID.randomUUID();

        internal(get("/internal/v1/parcels/" + unknown)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        internal(get("/internal/v1/parcels/" + unknown + "/recipient")).andExpect(status().isNotFound());
        transition(unknown, body("PICKED_UP", "COURIER", null, null)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TST-035 NFR-SEC-017: a missing or wrong X-Internal-Key is 401, and a user token is not enough")
    void internalKeyRequired() throws Exception {
        Parcel parcel = save();
        String path = "/internal/v1/parcels/" + parcel.getId();

        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("X-Internal-Key", internalKey + "x")).andExpect(status().isUnauthorized());
        mvc.perform(get(path + "/recipient").header("Authorization", TOKENS.admin().bearer()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path + "/transition").contentType("application/json")
                        .content(body("PICKED_UP", "COURIER", null, null)))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcel.getId()))
                .isEqualTo("CREATED");
    }

    private Parcel save() {
        Parcel parcel = parcels.saveAndFlush(ParcelFixtures.parcel(new TrackingNumberGenerator().generate(),
                customer, null, null));
        created.add(parcel.getId());
        return parcel;
    }

    private String body(String toStatus, String actorRole, String failureReason, String recipientName) {
        return """
                {"toStatus":"%s","actorUserId":"%s","actorRole":"%s"%s%s}
                """.formatted(toStatus, courier, actorRole,
                failureReason == null ? "" : ",\"failureReason\":\"" + failureReason + "\"",
                recipientName == null ? "" : ",\"recipientName\":\"" + recipientName + "\"");
    }

    private ResultActions internal(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mvc.perform(request.header("X-Internal-Key", internalKey));
    }

    private ResultActions transition(UUID id, String body) throws Exception {
        return internal(post("/internal/v1/parcels/" + id + "/transition").contentType("application/json")
                .content(body));
    }
}

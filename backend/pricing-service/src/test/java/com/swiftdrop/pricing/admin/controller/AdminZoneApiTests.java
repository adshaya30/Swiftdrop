package com.swiftdrop.pricing.admin.controller;

import static com.swiftdrop.pricing.support.PricingTestConfiguration.TOKENS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.pricing.support.AbstractPricingDatabaseTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** FR-PRC-006, FR-PRC-008, AT-28, AT-39 (zones and districts). */
@Tag("TST-029")
@Transactional
class AdminZoneApiTests extends AbstractPricingDatabaseTest {
    private static final String ADMIN = "/api/v1/admin";
    private static final String WESTERN = "f80dc738-67e9-4b29-9109-5eee95536727";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String admin() { return TOKENS.admin().bearer(); }

    private JsonNode createZone(String code, String name) throws Exception {
        var body = mvc.perform(post(ADMIN + "/zones").header("Authorization", admin())
                        .contentType("application/json").content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private int audit(String action) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE routing_key = 'audit.recorded' "
                + "AND payload::text LIKE ?", Integer.class, "%" + action + "%");
        return n == null ? 0 : n;
    }

    private java.math.BigDecimal quote(String destination) throws Exception {
        var body = mvc.perform(post("/api/v1/public/quotes").contentType("application/json")
                        .content("{\"originDistrictCode\":\"COLOMBO\",\"destinationDistrictCode\":\"" + destination
                                + "\",\"weightKg\":2.00,\"size\":\"MEDIUM\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("total").decimalValue();
    }

    @Test
    @DisplayName("TST-029 FR-PRC-006: list returns the seeded zones with their districts")
    void listZones() throws Exception {
        var body = json.readTree(mvc.perform(get(ADMIN + "/zones").header("Authorization", admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(body).hasSize(6);
        JsonNode western = null;
        for (JsonNode zone : body) {
            if ("WESTERN".equals(zone.get("code").asText())) {
                western = zone;
            }
        }
        assertThat(western).isNotNull();
        assertThat(western.get("active").asBoolean()).isTrue();
        assertThat(western.get("districts")).hasSize(3);
    }

    @Test
    @DisplayName("TST-029 FR-PRC-006/008 AT-39: create writes one ZONE_CREATED audit row without contact data")
    void createZoneAudited() throws Exception {
        var zone = createZone("EAST_COAST", "East Coast");
        assertThat(zone.get("code").asText()).isEqualTo("EAST_COAST");
        assertThat(zone.get("active").asBoolean()).isTrue();
        assertThat(zone.get("districts")).isEmpty();
        assertThat(audit("ZONE_CREATED")).isEqualTo(1);
        String payload = jdbc.queryForObject("SELECT payload::text FROM outbox_events WHERE routing_key = "
                + "'audit.recorded' AND payload::text LIKE '%ZONE_CREATED%'", String.class).toLowerCase();
        assertThat(payload).doesNotContain("password", "email", "phone", "secret", "token", "address");
    }

    @Test
    @DisplayName("TST-029 FR-PRC-006: a duplicate code or name answers 400 VALIDATION_ERROR (SRS gap)")
    void duplicateZone() throws Exception {
        mvc.perform(post(ADMIN + "/zones").header("Authorization", admin()).contentType("application/json")
                        .content("{\"code\":\"WESTERN\",\"name\":\"Another\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("code")));
        mvc.perform(post(ADMIN + "/zones").header("Authorization", admin()).contentType("application/json")
                        .content("{\"code\":\"BRAND_NEW\",\"name\":\"Western\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")));
        assertThat(audit("ZONE_CREATED")).isZero();
    }

    @Test
    @DisplayName("TST-029 FR-PRC-006: code must match [A-Z_]{2,20}; unknown properties are rejected")
    void zoneValidation() throws Exception {
        for (String body : List.of("{\"code\":\"east\",\"name\":\"East\"}", "{\"code\":\"E\",\"name\":\"East\"}",
                "{\"code\":\"A_VERY_LONG_ZONE_CODE_X\",\"name\":\"East\"}", "{\"code\":\"EAST1\",\"name\":\"East\"}",
                "{\"code\":\"EAST\",\"name\":\"E\"}", "{\"code\":\"EAST\"}",
                "{\"code\":\"EAST\",\"name\":\"East\",\"active\":false}")) {
            mvc.perform(post(ADMIN + "/zones").header("Authorization", admin()).contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    @DisplayName("TST-029 FR-PRC-006: the code is immutable (PATCH rejects it) and a rename is audited")
    void renameZone() throws Exception {
        String id = createZone("RENAME_ME", "Rename Me").get("id").asText();
        mvc.perform(patch(ADMIN + "/zones/" + id).header("Authorization", admin()).contentType("application/json")
                        .content("{\"code\":\"OTHER\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(patch(ADMIN + "/zones/" + id).header("Authorization", admin()).contentType("application/json")
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.code").value("RENAME_ME"));
        assertThat(audit("ZONE_UPDATED")).isEqualTo(1);
    }

    @Test
    @DisplayName("TST-029 FR-PRC-006: PATCH needs a name or an active flag; unknown zone is 404; duplicate name is 400")
    void patchEdgeCases() throws Exception {
        String id = createZone("PATCH_EDGE", "Patch Edge").get("id").asText();
        mvc.perform(patch(ADMIN + "/zones/" + id).header("Authorization", admin()).contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(patch(ADMIN + "/zones/" + UUID.randomUUID()).header("Authorization", admin())
                        .contentType("application/json").content("{\"name\":\"Whatever\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(patch(ADMIN + "/zones/" + id).header("Authorization", admin()).contentType("application/json")
                        .content("{\"name\":\"Western\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("name")));
    }

    @Test
    @DisplayName("TST-029 FR-PRC-006: a zone that still has districts cannot be deactivated (409 ZONE_IN_USE)")
    void zoneInUse() throws Exception {
        mvc.perform(patch(ADMIN + "/zones/" + WESTERN).header("Authorization", admin())
                        .contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ZONE_IN_USE"));
        assertThat(jdbc.queryForObject("SELECT active FROM zones WHERE id = ?::uuid", Boolean.class, WESTERN)).isTrue();
        assertThat(audit("ZONE_UPDATED")).isZero();
    }

    @Test
    @DisplayName("TST-029 FR-PRC-006: an empty zone can be deactivated and reactivated; a district cannot move into an inactive zone")
    void deactivateEmptyZone() throws Exception {
        String id = createZone("EMPTY_ZONE", "Empty Zone").get("id").asText();
        mvc.perform(patch(ADMIN + "/zones/" + id).header("Authorization", admin()).contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(put(ADMIN + "/districts/KANDY/zone").header("Authorization", admin())
                        .contentType("application/json").content("{\"zoneId\":\"" + id + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("zoneId")));
        mvc.perform(patch(ADMIN + "/zones/" + id).header("Authorization", admin()).contentType("application/json")
                        .content("{\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("TST-029 FR-PRC-006/007 AT-28: moving a district and changing a price change later quotes")
    void districtMoveChangesLaterQuotes() throws Exception {
        assertThat(quote("KANDY")).isEqualByComparingTo("800.00");
        String newZone = createZone("HILL_COUNTRY", "Hill Country").get("id").asText();

        // Without a pricing row for the new pair the district is no longer quotable (FR-PRC-004).
        mvc.perform(put(ADMIN + "/districts/KANDY/zone").header("Authorization", admin())
                        .contentType("application/json").content("{\"zoneId\":\"" + newZone + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("HILL_COUNTRY"))
                .andExpect(jsonPath("$.districts[*].code", hasItem("KANDY")));
        mvc.perform(post("/api/v1/public/quotes").contentType("application/json")
                        .content("{\"originDistrictCode\":\"COLOMBO\",\"destinationDistrictCode\":\"KANDY\","
                                + "\"weightKg\":2.00,\"size\":\"MEDIUM\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRICING_NOT_CONFIGURED"));

        mvc.perform(put(ADMIN + "/pricing/" + WESTERN + "/" + newZone).header("Authorization", admin())
                        .contentType("application/json").content("{\"baseFee\":900.00,\"perKgFee\":200.00}"))
                .andExpect(status().isOk());
        // 900 + (ceil(2) - 1) x 200 + 150 (MEDIUM) = 1250
        assertThat(quote("KANDY")).isEqualByComparingTo("1250.00");
        assertThat(audit("DISTRICT_MOVED")).isEqualTo(1);
        assertThat(audit("PRICING_UPDATED")).isEqualTo(1);
    }

    @Test
    @DisplayName("TST-029 FR-PRC-006: unknown district or zone is 404; moving to the same zone is a no-op without audit")
    void moveEdgeCases() throws Exception {
        mvc.perform(put(ADMIN + "/districts/NOWHERE/zone").header("Authorization", admin())
                        .contentType("application/json").content("{\"zoneId\":\"" + WESTERN + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put(ADMIN + "/districts/KANDY/zone").header("Authorization", admin())
                        .contentType("application/json").content("{\"zoneId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put(ADMIN + "/districts/KANDY/zone").header("Authorization", admin())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(ADMIN + "/districts/COLOMBO/zone").header("Authorization", admin())
                        .contentType("application/json").content("{\"zoneId\":\"" + WESTERN + "\"}"))
                .andExpect(status().isOk());
        assertThat(audit("DISTRICT_MOVED")).isZero();
    }
}

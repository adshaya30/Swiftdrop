package com.swiftdrop.pricing.admin.controller;

import static com.swiftdrop.pricing.support.PricingTestConfiguration.TOKENS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.pricing.support.AbstractPricingDatabaseTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** FR-PRC-007, FR-PRC-008, AT-28, AT-39 (pricing matrix and size surcharges). */
@Tag("TST-029")
@Transactional
class AdminPricingApiTests extends AbstractPricingDatabaseTest {
    private static final String ADMIN = "/api/v1/admin";
    private static final String WESTERN = "f80dc738-67e9-4b29-9109-5eee95536727";
    private static final String CENTRAL = "33797426-585d-4797-8276-716f160861c2";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String admin() { return TOKENS.admin().bearer(); }

    private int audit(String action) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE routing_key = 'audit.recorded' "
                + "AND payload::text LIKE ?", Integer.class, "%" + action + "%");
        return n == null ? 0 : n;
    }

    private BigDecimal quote(String size) throws Exception {
        var body = mvc.perform(post("/api/v1/public/quotes").contentType("application/json")
                        .content("{\"originDistrictCode\":\"COLOMBO\",\"destinationDistrictCode\":\"KANDY\","
                                + "\"weightKg\":2.00,\"size\":\"" + size + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("total").decimalValue();
    }

    private static String surcharges(String small, String medium, String large) {
        return "[{\"size\":\"SMALL\",\"surcharge\":" + small + "},{\"size\":\"MEDIUM\",\"surcharge\":" + medium
                + "},{\"size\":\"LARGE\",\"surcharge\":" + large + "}]";
    }

    @Test
    @DisplayName("TST-029 FR-PRC-007: the matrix lists all 36 seeded directed pairs")
    void matrix() throws Exception {
        var body = json.readTree(mvc.perform(get(ADMIN + "/pricing").header("Authorization", admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(body).hasSize(36);
        JsonNode first = body.get(0);
        assertThat(first.fieldNames()).toIterable().containsExactlyInAnyOrder("originZoneId", "originZoneCode",
                "destinationZoneId", "destinationZoneCode", "baseFee", "perKgFee");
    }

    @Test
    @DisplayName("TST-029 FR-PRC-007/008 AT-28: a price change affects later quotes and is audited")
    void priceChangeAffectsLaterQuotes() throws Exception {
        assertThat(quote("MEDIUM")).isEqualByComparingTo("800.00");
        mvc.perform(put(ADMIN + "/pricing/" + WESTERN + "/" + CENTRAL).header("Authorization", admin())
                        .contentType("application/json").content("{\"baseFee\":600,\"perKgFee\":200.5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.originZoneCode").value("WESTERN"))
                .andExpect(jsonPath("$.destinationZoneCode").value("CENTRAL"));
        // 600.00 + (2 - 1) x 200.50 + 150.00 (MEDIUM)
        assertThat(quote("MEDIUM")).isEqualByComparingTo("950.50");
        // The reverse direction is a different pair and stays unchanged (matrix is directed).
        assertThat(jdbc.queryForObject("SELECT base_fee FROM zone_pricing WHERE origin_zone_id = ?::uuid AND "
                + "destination_zone_id = ?::uuid", BigDecimal.class, CENTRAL, WESTERN)).isEqualByComparingTo("500.00");
        assertThat(audit("PRICING_UPDATED")).isEqualTo(1);
        String payload = jdbc.queryForObject("SELECT payload::text FROM outbox_events WHERE routing_key = "
                + "'audit.recorded' AND payload::text LIKE '%PRICING_UPDATED%'", String.class).toLowerCase();
        assertThat(payload).doesNotContain("password", "email", "phone", "secret", "token", "address");
    }

    @Test
    @DisplayName("TST-029 FR-PRC-007: PUT creates a missing zone pair; the limit 1,000,000.00 is accepted")
    void createsMissingPair() throws Exception {
        String zoneId = json.readTree(mvc.perform(post(ADMIN + "/zones").header("Authorization", admin())
                        .contentType("application/json").content("{\"code\":\"NEW_ZONE\",\"name\":\"New Zone\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(put(ADMIN + "/pricing/" + WESTERN + "/" + zoneId).header("Authorization", admin())
                        .contentType("application/json").content("{\"baseFee\":1000000.00,\"perKgFee\":0}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM zone_pricing", Integer.class)).isEqualTo(37);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"baseFee\":-0.01,\"perKgFee\":1}", "{\"baseFee\":1,\"perKgFee\":-1}",
            "{\"baseFee\":1.001,\"perKgFee\":1}", "{\"baseFee\":1,\"perKgFee\":10.123}",
            "{\"baseFee\":1000000.01,\"perKgFee\":1}", "{\"baseFee\":1,\"perKgFee\":1000000.01}",
            "{\"perKgFee\":1}", "{\"baseFee\":1}", "{\"baseFee\":\"abc\",\"perKgFee\":1}",
            "{\"baseFee\":1,\"perKgFee\":1,\"extra\":true}"})
    @DisplayName("TST-029 FR-PRC-007: invalid fees are rejected with 400 VALIDATION_ERROR and nothing changes")
    void invalidFees(String body) throws Exception {
        mvc.perform(put(ADMIN + "/pricing/" + WESTERN + "/" + CENTRAL).header("Authorization", admin())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(audit("PRICING_UPDATED")).isZero();
        assertThat(quote("MEDIUM")).isEqualByComparingTo("800.00");
    }

    @Test
    @DisplayName("TST-029 FR-PRC-007: an unknown zone in the pricing path is 404")
    void unknownZone() throws Exception {
        mvc.perform(put(ADMIN + "/pricing/" + UUID.randomUUID() + "/" + CENTRAL).header("Authorization", admin())
                        .contentType("application/json").content("{\"baseFee\":1,\"perKgFee\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(put(ADMIN + "/pricing/" + WESTERN + "/" + UUID.randomUUID()).header("Authorization", admin())
                        .contentType("application/json").content("{\"baseFee\":1,\"perKgFee\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TST-029 FR-PRC-007: GET size-surcharges lists SMALL, MEDIUM and LARGE with descriptions")
    void listSurcharges() throws Exception {
        mvc.perform(get(ADMIN + "/size-surcharges").header("Authorization", admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].size").value("SMALL")).andExpect(jsonPath("$[1].size").value("MEDIUM"))
                .andExpect(jsonPath("$[2].size").value("LARGE")).andExpect(jsonPath("$[0].description").exists());
    }

    @Test
    @DisplayName("TST-029 FR-PRC-007/008 AT-28: PUT size-surcharges changes later quotes and is audited")
    void updateSurcharges() throws Exception {
        // 500 + (2 - 1) x 150 + 400 (seeded LARGE)
        assertThat(quote("LARGE")).isEqualByComparingTo("1050.00");
        mvc.perform(put(ADMIN + "/size-surcharges").header("Authorization", admin())
                        .contentType("application/json").content(surcharges("0", "200.00", "500.00")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        // 500 + 150 + 500
        assertThat(quote("LARGE")).isEqualByComparingTo("1150.00");
        assertThat(audit("SURCHARGES_UPDATED")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[]",
            "[{\"size\":\"SMALL\",\"surcharge\":1},{\"size\":\"MEDIUM\",\"surcharge\":1}]",
            "[{\"size\":\"SMALL\",\"surcharge\":1},{\"size\":\"SMALL\",\"surcharge\":1},{\"size\":\"LARGE\",\"surcharge\":1}]",
            "[{\"size\":\"HUGE\",\"surcharge\":1},{\"size\":\"MEDIUM\",\"surcharge\":1},{\"size\":\"LARGE\",\"surcharge\":1}]",
            "[{\"size\":\"SMALL\",\"surcharge\":-1},{\"size\":\"MEDIUM\",\"surcharge\":1},{\"size\":\"LARGE\",\"surcharge\":1}]",
            "[{\"size\":\"SMALL\",\"surcharge\":1.001},{\"size\":\"MEDIUM\",\"surcharge\":1},{\"size\":\"LARGE\",\"surcharge\":1}]",
            "[{\"size\":\"SMALL\"},{\"size\":\"MEDIUM\",\"surcharge\":1},{\"size\":\"LARGE\",\"surcharge\":1}]",
            "{\"SMALL\":1}"})
    @DisplayName("TST-029 FR-PRC-007: an invalid surcharge list is rejected with 400 and nothing changes")
    void invalidSurcharges(String body) throws Exception {
        mvc.perform(put(ADMIN + "/size-surcharges").header("Authorization", admin())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(audit("SURCHARGES_UPDATED")).isZero();
        assertThat(quote("LARGE")).isEqualByComparingTo("1050.00");
    }

    @Test
    @DisplayName("TST-029 FR-PRC-007: a duplicate size is reported on the size field")
    void duplicateSizeField() throws Exception {
        mvc.perform(put(ADMIN + "/size-surcharges").header("Authorization", admin())
                        .contentType("application/json")
                        .content("[{\"size\":\"SMALL\",\"surcharge\":1},{\"size\":\"SMALL\",\"surcharge\":2},"
                                + "{\"size\":\"LARGE\",\"surcharge\":3}]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("size")));
    }
}

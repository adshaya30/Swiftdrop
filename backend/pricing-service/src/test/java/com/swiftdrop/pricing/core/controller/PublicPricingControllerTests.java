package com.swiftdrop.pricing.core.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.pricing.support.AbstractPricingDatabaseTest;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("TST-010")
@Transactional
class PublicPricingControllerTests extends AbstractPricingDatabaseTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    static String request(String origin, String destination, String weight, String size) {
        return "{\"originDistrictCode\":\"" + origin + "\",\"destinationDistrictCode\":\"" + destination
                + "\",\"weightKg\":" + weight + ",\"size\":\"" + size + "\"}";
    }

    static Set<String> fields(JsonNode node) {
        Set<String> result = new HashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }

    @ParameterizedTest
    @CsvSource({"KANDY,2.00,MEDIUM,800", "KANDY,2.00,SMALL,650", "GAMPAHA,1.00,SMALL,350",
            "GAMPAHA,1.01,SMALL,450", "KANDY,1.00,LARGE,900", "KANDY,30.00,SMALL,4850"})
    @DisplayName("TST-010 FR-PRC-002 AT-05/06: public quotes use seeded prices without authentication")
    void publicQuotes(String destination, String weight, String size, int total) throws Exception {
        mvc.perform(post("/api/v1/public/quotes").contentType("application/json")
                        .content(request("COLOMBO", destination, weight, size)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currency").value("LKR"))
                .andExpect(jsonPath("$.total").value(total)).andExpect(jsonPath("$.discountAmount").value(0))
                .andExpect(jsonPath("$.originZone.code").value("WESTERN"));
    }

    @Test
    @DisplayName("FR-PRC-002 SRS 7.5: public response has exactly the quote contract")
    void exactQuoteSchema() throws Exception {
        var response = mvc.perform(post("/api/v1/public/quotes").contentType("application/json")
                        .content(request("COLOMBO", "KANDY", "2.00", "MEDIUM")))
                .andExpect(status().isOk()).andReturn().getResponse();
        var body = json.readTree(response.getContentAsString());
        assertThat(fields(body)).containsExactlyInAnyOrder("currency", "originZone", "destinationZone", "baseFee",
                "weightFee", "sizeSurcharge", "discountAmount", "total");
        assertThat(fields(body.get("originZone"))).containsExactlyInAnyOrder("code", "name");
        assertThat(fields(body.get("destinationZone"))).containsExactlyInAnyOrder("code", "name");
        assertThat(body.get("baseFee").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(body.get("weightFee").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(body.get("sizeSurcharge").decimalValue()).isEqualByComparingTo("150.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01", "30.01", "2.001", "null"})
    @DisplayName("TST-010 FR-PRC-004: weight violations have the weightKg field and safe Problem Details")
    void invalidWeight(String weight) throws Exception {
        mvc.perform(post("/api/v1/public/quotes").contentType("application/json")
                        .header("X-Request-Id", "pricing-validation-test")
                        .content(request("COLOMBO", "KANDY", weight, "SMALL")))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("weightKg")))
                .andExpect(jsonPath("$.traceId").value("pricing-validation-test"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{}", "null",
            "{\"originDistrictCode\":\"COLOMBO\",\"destinationDistrictCode\":\"KANDY\",\"weightKg\":\"bad\",\"size\":\"SMALL\"}",
            "{\"originDistrictCode\":\"COLOMBO\",\"destinationDistrictCode\":\"KANDY\",\"weightKg\":1,\"size\":\"HUGE\"}"})
    @DisplayName("FR-PRC-004 SRS 7.2: malformed, missing and invalid input is rejected")
    void invalidBody(String body) throws Exception {
        mvc.perform(post("/api/v1/public/quotes").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("FR-PRC-004: unknown properties and public discount injection are rejected")
    void unknownProperties() throws Exception {
        String valid = request("COLOMBO", "KANDY", "1", "SMALL");
        for (String property : new String[]{"discountPercent", "extra"}) {
            mvc.perform(post("/api/v1/public/quotes").contentType("application/json")
                            .content(valid.substring(0, valid.length() - 1) + ",\"" + property + "\":10}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @ParameterizedTest
    @CsvSource({"UNKNOWN,KANDY,originDistrictCode", "COLOMBO,UNKNOWN,destinationDistrictCode", "'',KANDY,originDistrictCode",
            "COLOMBO,ABCDEFGHIJKLMNOPQRSTU,destinationDistrictCode"})
    @DisplayName("FR-PRC-004: unknown, blank and overlength district codes are field errors")
    void invalidDistrict(String origin, String destination, String field) throws Exception {
        mvc.perform(post("/api/v1/public/quotes").contentType("application/json").content(request(origin, destination, "1", "SMALL")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));
    }

    @Test
    @DisplayName("TST-010 FR-PRC-004: inactive zone is ZONE_NOT_SERVICEABLE")
    void inactiveZone() throws Exception {
        jdbc.update("UPDATE zones SET active=false WHERE code='CENTRAL'");
        mvc.perform(post("/api/v1/public/quotes").contentType("application/json").content(request("COLOMBO", "KANDY", "1", "SMALL")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ZONE_NOT_SERVICEABLE"));
    }

    @Test
    @DisplayName("TST-010 FR-PRC-004: missing directed price is PRICING_NOT_CONFIGURED")
    void missingPrice() throws Exception {
        jdbc.update("DELETE FROM zone_pricing WHERE origin_zone_id=(SELECT id FROM zones WHERE code='WESTERN') "
                + "AND destination_zone_id=(SELECT id FROM zones WHERE code='CENTRAL')");
        mvc.perform(post("/api/v1/public/quotes").contentType("application/json").content(request("COLOMBO", "KANDY", "1", "SMALL")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PRICING_NOT_CONFIGURED"));
        mvc.perform(post("/api/v1/public/quotes").contentType("application/json").content(request("KANDY", "COLOMBO", "1", "SMALL")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("FR-PRC-005/009: districts schema, sizes, configured maximum and active-only filtering")
    void activeDistricts() throws Exception {
        var response = mvc.perform(get("/api/v1/public/districts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.maxWeightKg").value(30)).andExpect(jsonPath("$.districts.length()").value(25))
                .andExpect(jsonPath("$.sizes.length()").value(3)).andReturn().getResponse();
        var body = json.readTree(response.getContentAsString());
        assertThat(fields(body)).containsExactlyInAnyOrder("maxWeightKg", "sizes", "districts");
        assertThat(fields(body.get("districts").get(0))).containsExactlyInAnyOrder("code", "name", "zoneCode", "zoneName");
        assertThat(fields(body.get("sizes").get(0))).containsExactlyInAnyOrder("size", "surcharge", "description");
        assertThat(body.get("sizes").get(0).get("size").asText()).isEqualTo("SMALL");
        assertThat(body.get("sizes").get(1).get("surcharge").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(body.get("sizes").get(2).get("description").asText()).isEqualTo("80 x 60 x 50 cm");
        jdbc.update("UPDATE zones SET active=false WHERE code='WESTERN'");
        String filtered = mvc.perform(get("/api/v1/public/districts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.districts.length()").value(22)).andReturn().getResponse().getContentAsString();
        for (JsonNode district : json.readTree(filtered).get("districts")) {
            assertThat(district.get("zoneCode").asText()).isNotEqualTo("WESTERN");
        }
    }
}

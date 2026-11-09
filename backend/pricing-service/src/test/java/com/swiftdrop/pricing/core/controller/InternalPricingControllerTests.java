package com.swiftdrop.pricing.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.pricing.support.AbstractPricingDatabaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static com.swiftdrop.pricing.core.controller.PublicPricingControllerTests.*;
import static com.swiftdrop.pricing.support.PricingTestConfiguration.TOKENS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("TST-010")
@Transactional
@TestPropertySource(properties = "SWAGGER_ENABLED=true")
class InternalPricingControllerTests extends AbstractPricingDatabaseTest {
    @Autowired MockMvc mvc;
    @Autowired Environment environment;
    @Autowired ObjectMapper json;

    private String key() { return environment.getRequiredProperty("INTERNAL_API_KEY"); }

    static String discounted(String discount) {
        String base = request("COLOMBO", "JAFFNA", "5.00", "LARGE");
        return base.substring(0, base.length() - 1) + ",\"discountPercent\":" + discount + "}";
    }

    @Test
    @DisplayName("TST-010 AT-07 FR-PRC-003: exact internal calculation schema and district snapshots")
    void discountedCalculation() throws Exception {
        var response = mvc.perform(post("/internal/v1/pricing/calculate").header("X-Internal-Key", key())
                        .header("Authorization", "Bearer ignored-on-internal-chain").contentType("application/json").content(discounted("10")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currency").value("LKR"))
                .andExpect(jsonPath("$.subtotal").value(1800)).andExpect(jsonPath("$.discountAmount").value(180))
                .andExpect(jsonPath("$.total").value(1620)).andExpect(jsonPath("$.discountPercent").value(10))
                .andExpect(jsonPath("$.originDistrict.code").value("COLOMBO"))
                .andExpect(jsonPath("$.destinationDistrict.name").value("Jaffna"))
                .andExpect(jsonPath("$.destinationZone.code").value("NORTH_EAST"))
                .andReturn().getResponse();
        var body = json.readTree(response.getContentAsString());
        assertThat(fields(body)).containsExactlyInAnyOrder("currency", "originZone", "destinationZone", "baseFee", "weightFee",
                "sizeSurcharge", "discountAmount", "total", "originDistrict", "destinationDistrict", "subtotal", "discountPercent");
        assertThat(fields(body.get("originDistrict"))).containsExactlyInAnyOrder("code", "name");
        assertThat(fields(body.get("destinationDistrict"))).containsExactlyInAnyOrder("code", "name");
    }

    @Test
    @DisplayName("FR-PRC-003: absent or null discount defaults to zero")
    void defaultDiscount() throws Exception {
        for (String body : new String[]{request("COLOMBO", "JAFFNA", "5", "LARGE"), discounted("null")}) {
            mvc.perform(post("/internal/v1/pricing/calculate").header("X-Internal-Key", key()).contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.discountPercent").value(0))
                    .andExpect(jsonPath("$.discountAmount").value(0)).andExpect(jsonPath("$.total").value(1800));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "50"})
    @DisplayName("TST-010 BR-13: internal discount boundaries are accepted")
    void validDiscount(String discount) throws Exception {
        mvc.perform(post("/internal/v1/pricing/calculate").header("X-Internal-Key", key()).contentType("application/json").content(discounted(discount)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(discount.equals("0") ? 1800 : 900));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "50.01", "100"})
    @DisplayName("TST-010 BR-13: discount outside zero to fifty is a field validation error")
    void invalidDiscount(String discount) throws Exception {
        mvc.perform(post("/internal/v1/pricing/calculate").header("X-Internal-Key", key()).contentType("application/json").content(discounted(discount)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("discountPercent")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "30.01", "2.001", "null"})
    @DisplayName("TST-010 FR-PRC-004: internal endpoint enforces the same weight validation")
    void invalidWeight(String weight) throws Exception {
        mvc.perform(post("/internal/v1/pricing/calculate").header("X-Internal-Key", key()).contentType("application/json")
                        .content(request("COLOMBO", "JAFFNA", weight, "LARGE")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("weightKg")));
    }

    @Test
    @Tag("TST-035")
    @DisplayName("TST-035: missing/wrong internal key and JWT alone are rejected")
    void internalKeyRequired() throws Exception {
        String body = discounted("10");
        mvc.perform(post("/internal/v1/pricing/calculate").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/internal/v1/pricing/calculate").header("X-Internal-Key", "wrong").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/v1/pricing/calculate").header("Authorization", TOKENS.admin().bearer()).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("FR-PRC-003/004: internal unknown JSON properties are rejected")
    void unknownProperty() throws Exception {
        String body = discounted("10");
        mvc.perform(post("/internal/v1/pricing/calculate").header("X-Internal-Key", key()).contentType("application/json")
                        .content(body.substring(0, body.length() - 1) + ",\"unexpected\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("SRS 7.4: OpenAPI documents real endpoint paths and internal header security")
    void openApiContract() throws Exception {
        mvc.perform(get("/v3/api-docs").header("Authorization", TOKENS.customer().bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/v1/public/quotes'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/public/districts'].get").exists())
                .andExpect(jsonPath("$.paths['/internal/v1/pricing/calculate'].post.security[0].internalKey").exists())
                .andExpect(jsonPath("$.components.securitySchemes.internalKey.name").value("X-Internal-Key"));
    }
}

package com.swiftdrop.pricing.core.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;
import com.swiftdrop.common.security.SecurityAutoConfiguration;
import com.swiftdrop.pricing.support.PricingTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.swiftdrop.pricing.support.PricingTestConfiguration.TOKENS;

@WebMvcTest(properties = "INTERNAL_API_KEY=pricing-foundation-test-key-32-characters")
@Import({PricingSecurityConfiguration.class, SecurityAutoConfiguration.class, PricingTestConfiguration.class,
        PricingSecurityConfigurationTests.ProbeController.class})
class PricingSecurityConfigurationTests {
    @Autowired MockMvc mvc;
    @Test
    @DisplayName("SRS 3.4: only the service's declared public paths are allowed")
    void rulesAreExact() {
        var rules = new PricingSecurityConfiguration().securityRules();
        assertThat(rules.publicPaths()).containsExactly("GET /api/v1/public/districts", "POST /api/v1/public/quotes");
        assertThat(rules.passwordChangeWhitelist()).isEmpty();
    }

    @Test
    @DisplayName("SRS 3.4: declared public methods need no JWT, protected methods do")
    void methodSpecificPublicAccess() throws Exception {
        mvc.perform(get("/api/v1/public/districts")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/public/quotes")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/public/districts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/foundation-protected")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/foundation-protected").header("Authorization", TOKENS.customer().bearer()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SRS 3.6: internal key, token type and empty password-change whitelist use shared filters")
    void sharedFiltersAreActive() throws Exception {
        mvc.perform(get("/internal/v1/foundation")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/foundation").header("Authorization", TOKENS.admin().bearer()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/foundation").header("X-Internal-Key", "pricing-foundation-test-key-32-characters"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/foundation-protected").header("Authorization", TOKENS.client(java.util.UUID.randomUUID(), "orders:read").bearer()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/foundation-protected").header("Authorization", TOKENS.customer().passwordChangeRequired(true).bearer()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    @DisplayName("SRS 7.2: unknown JSON properties are rejected by the configured shared handler")
    void rejectsUnknownJsonProperties() throws Exception {
        mvc.perform(post("/api/v1/public/quotes").contentType("application/json").content("{\"value\":\"ok\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/public/quotes").contentType("application/json").content("{\"unexpected\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /** Test-only responses exercise filter wiring; production pricing endpoints belong to PR B. */
    @RestController
    static class ProbeController {
        @GetMapping({"/api/v1/public/districts", "/api/v1/foundation-protected", "/internal/v1/foundation"})
        String getProbe() { return "ok"; }
        @PostMapping("/api/v1/public/quotes")
        String postProbe(@RequestBody(required = false) ProbeBody body) { return "ok"; }
    }

    record ProbeBody(String value) { }
}

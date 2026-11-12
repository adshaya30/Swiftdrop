package com.swiftdrop.parcel.create.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;
import com.swiftdrop.common.security.SecurityAutoConfiguration;
import com.swiftdrop.parcel.support.ParcelTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.swiftdrop.parcel.support.ParcelTestConfiguration.TOKENS;

@WebMvcTest(controllers = ParcelSecurityConfigurationTests.ProbeController.class,
        properties = "INTERNAL_API_KEY=parcel-foundation-test-key-32-characters")
@Import({ParcelSecurityConfiguration.class, SecurityAutoConfiguration.class, ParcelTestConfiguration.class,
        ParcelSecurityConfigurationTests.ProbeController.class})
class ParcelSecurityConfigurationTests {
    @Autowired MockMvc mvc;
    @Test
    @DisplayName("SRS 3.4: only the service's declared public paths are allowed")
    void rulesAreExact() {
        var rules = new ParcelSecurityConfiguration().securityRules();
        assertThat(rules.publicPaths()).isEmpty();
        assertThat(rules.passwordChangeWhitelist()).isEmpty();
    }

    @Test
    @DisplayName("SRS 3.4/3.6: parcel paths require JWT and internal paths require only the key")
    void sharedSecurityIsActive() throws Exception {
        mvc.perform(get("/api/v1/foundation-protected")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/public/districts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/foundation-protected").header("Authorization", TOKENS.customer().bearer()))
                .andExpect(status().isOk());
        mvc.perform(get("/internal/v1/foundation")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/foundation").header("X-Internal-Key", "parcel-foundation-test-key-32-characters"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/foundation-protected").header("Authorization", TOKENS.client(java.util.UUID.randomUUID(), "orders:read").bearer()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/foundation-protected").header("Authorization", TOKENS.customer().passwordChangeRequired(true).bearer()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/api/v1/foundation-protected", "/internal/v1/foundation"})
        String probe() { return "ok"; }
    }
}

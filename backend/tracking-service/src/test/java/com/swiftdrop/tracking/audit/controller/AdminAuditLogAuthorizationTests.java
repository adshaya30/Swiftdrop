package com.swiftdrop.tracking.audit.controller;

import static com.swiftdrop.tracking.audit.support.AuditTestConfiguration.TOKENS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftdrop.tracking.audit.support.AbstractAuditDatabaseTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** TST-033 BR-25 SRS 3.6: the audit-log endpoint is ADMIN only; client tokens are refused. */
@Tag("TST-033")
@Transactional
class AdminAuditLogAuthorizationTests extends AbstractAuditDatabaseTest {
    private static final String URL = "/api/v1/admin/audit-logs";

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("TST-033 BR-25: unauthenticated callers get 401 UNAUTHENTICATED")
    void unauthenticated() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("TST-033 BR-25: CUSTOMER, MERCHANT, COURIER and client tokens get 403 ACCESS_DENIED")
    void otherRolesAndClientTokens() throws Exception {
        String[] tokens = {TOKENS.customer().bearer(), TOKENS.merchant(UUID.randomUUID()).bearer(),
                TOKENS.courier().bearer(), TOKENS.client(UUID.randomUUID(), "orders:write").bearer()};
        for (String token : tokens) {
            mvc.perform(get(URL).header("Authorization", token)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    @DisplayName("TST-033 BR-25: an ADMIN token is accepted")
    void adminAllowed() throws Exception {
        mvc.perform(get(URL).header("Authorization", TOKENS.admin().bearer())).andExpect(status().isOk());
    }
}

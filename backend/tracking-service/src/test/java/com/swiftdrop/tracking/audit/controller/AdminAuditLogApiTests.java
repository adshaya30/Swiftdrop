package com.swiftdrop.tracking.audit.controller;

import static com.swiftdrop.tracking.audit.support.AuditTestConfiguration.TOKENS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.tracking.audit.service.AuditLogRetentionService;
import com.swiftdrop.tracking.audit.support.AbstractAuditDatabaseTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** FR-TRK-005, AT-39, NFR-SEC-019: admin audit-log API. */
@Tag("TST-029")
@Transactional
class AdminAuditLogApiTests extends AbstractAuditDatabaseTest {
    private static final String URL = "/api/v1/admin/audit-logs";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuditLogRetentionService retention;

    private JsonNode list(String query) throws Exception {
        return json.readTree(mvc.perform(get(URL + query).header("Authorization", TOKENS.admin().bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("TST-029 FR-TRK-005: entries are newest first with exactly the documented fields")
    void newestFirstAndShape() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID actor = UUID.randomUUID();
        insertAudit("ZONE_CREATED", actor, "ADMIN", now.minus(2, ChronoUnit.HOURS), "{\"zoneCode\":\"A_ZONE\"}");
        insertAudit("PRICING_UPDATED", actor, "ADMIN", now, "{\"newBaseFee\":600.00}");
        insertAudit("ZONE_UPDATED", actor, "ADMIN", now.minus(1, ChronoUnit.HOURS), null);

        JsonNode body = list("");
        assertThat(body.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("items", "page", "size", "totalItems", "totalPages");
        assertThat(body.get("totalItems").asInt()).isEqualTo(3);
        JsonNode items = body.get("items");
        assertThat(items.get(0).get("action").asText()).isEqualTo("PRICING_UPDATED");
        assertThat(items.get(1).get("action").asText()).isEqualTo("ZONE_UPDATED");
        assertThat(items.get(2).get("action").asText()).isEqualTo("ZONE_CREATED");
        assertThat(items.get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("id", "service",
                "actorUserId", "actorRole", "action", "entityType", "entityId", "details", "occurredAt");
        assertThat(items.get(0).get("details").isObject()).isTrue();
        assertThat(items.get(0).get("details").get("newBaseFee").decimalValue()).isEqualByComparingTo("600.00");
        assertThat(items.get(1).get("details").isNull()).isTrue();
        assertThat(items.get(0).get("actorUserId").asText()).isEqualTo(actor.toString());
    }

    @Test
    @DisplayName("TST-029 FR-TRK-005: filters by action and by actorId")
    void filtersByActionAndActor() throws Exception {
        Instant now = Instant.now();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        insertAudit("ZONE_CREATED", a, "ADMIN", now, "{}");
        insertAudit("ZONE_CREATED", b, "ADMIN", now.minusSeconds(5), "{}");
        insertAudit("PRICING_UPDATED", a, "ADMIN", now.minusSeconds(10), "{}");

        assertThat(list("?action=ZONE_CREATED").get("totalItems").asInt()).isEqualTo(2);
        assertThat(list("?actorId=" + a).get("totalItems").asInt()).isEqualTo(2);
        JsonNode both = list("?action=ZONE_CREATED&actorId=" + b);
        assertThat(both.get("totalItems").asInt()).isEqualTo(1);
        assertThat(both.get("items").get(0).get("actorUserId").asText()).isEqualTo(b.toString());
        assertThat(list("?action=NOTHING").get("items")).isEmpty();
    }

    @Test
    @DisplayName("TST-029 FR-TRK-005: from and to are inclusive Asia/Colombo dates (18:45 UTC is the next Colombo day)")
    void dateRangeInColombo() throws Exception {
        // 2026-03-01T18:45Z is 2026-03-02 00:15 in Asia/Colombo (UTC+05:30).
        insertAudit("EDGE", null, "SYSTEM", Instant.parse("2026-03-01T18:45:00Z"), "{}");
        // 2026-03-01T18:29Z is still 2026-03-01 23:59 in Asia/Colombo.
        insertAudit("BEFORE_EDGE", null, "SYSTEM", Instant.parse("2026-03-01T18:29:00Z"), "{}");

        assertThat(list("?from=2026-03-02&to=2026-03-02").get("items")).hasSize(1);
        assertThat(list("?from=2026-03-02&to=2026-03-02").get("items").get(0).get("action").asText())
                .isEqualTo("EDGE");
        assertThat(list("?from=2026-03-01&to=2026-03-01").get("items")).hasSize(1);
        assertThat(list("?from=2026-03-01&to=2026-03-01").get("items").get(0).get("action").asText())
                .isEqualTo("BEFORE_EDGE");
        assertThat(list("?from=2026-03-01&to=2026-03-02").get("totalItems").asInt()).isEqualTo(2);
        assertThat(list("?from=2026-03-03").get("items")).isEmpty();
        assertThat(list("?to=2026-02-28").get("items")).isEmpty();
    }

    @Test
    @DisplayName("TST-029 FR-TRK-005: pagination reports totals and honours page and size")
    void pagination() throws Exception {
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            insertAudit("ACTION_" + i, null, "SYSTEM", now.minusSeconds(i), "{}");
        }
        JsonNode second = list("?page=1&size=2");
        assertThat(second.get("page").asInt()).isEqualTo(1);
        assertThat(second.get("size").asInt()).isEqualTo(2);
        assertThat(second.get("totalItems").asInt()).isEqualTo(5);
        assertThat(second.get("totalPages").asInt()).isEqualTo(3);
        assertThat(second.get("items")).hasSize(2);
        assertThat(second.get("items").get(0).get("action").asText()).isEqualTo("ACTION_2");
        assertThat(list("?page=2&size=2").get("items")).hasSize(1);
        assertThat(list("?page=9&size=2").get("items")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"?size=0", "?size=101", "?page=-1", "?from=2026-03-05&to=2026-03-01", "?from=not-a-date",
            "?actorId=not-a-uuid", "?action=ABCDEFGHIJABCDEFGHIJABCDEFGHIJABCDEFGHIJABCDEFGHIJABCDEFGHIJX"})
    @DisplayName("TST-029 FR-TRK-005: invalid filters and paging are rejected with 400 VALIDATION_ERROR")
    void invalidParameters(String query) throws Exception {
        mvc.perform(get(URL + query).header("Authorization", TOKENS.admin().bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("TST-029 FR-TRK-005: the retention purge removes entries older than 1 year and keeps newer ones")
    void retentionPurge() throws Exception {
        Instant now = Instant.now();
        insertAudit("TOO_OLD", null, "SYSTEM", now.minus(400, ChronoUnit.DAYS), "{}");
        insertAudit("ALSO_OLD", null, "SYSTEM", now.minus(366, ChronoUnit.DAYS), "{}");
        insertAudit("KEPT_NEARLY_A_YEAR", null, "SYSTEM", now.minus(364, ChronoUnit.DAYS), "{}");
        insertAudit("KEPT_RECENT", null, "SYSTEM", now, "{}");

        int removed = retention.purgeOlderThan(now.minus(365, ChronoUnit.DAYS));

        assertThat(removed).isEqualTo(2);
        JsonNode remaining = list("");
        assertThat(remaining.get("totalItems").asInt()).isEqualTo(2);
        assertThat(remaining.get("items").get(0).get("action").asText()).isEqualTo("KEPT_RECENT");
        assertThat(remaining.get("items").get(1).get("action").asText()).isEqualTo("KEPT_NEARLY_A_YEAR");
        assertThat(retention.purgeOlderThan(now.minus(365, ChronoUnit.DAYS))).isZero();
    }
}

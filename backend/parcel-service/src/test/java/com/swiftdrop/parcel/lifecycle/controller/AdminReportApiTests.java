package com.swiftdrop.parcel.lifecycle.controller;

import static com.swiftdrop.parcel.support.ParcelTestConfiguration.TOKENS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelRepository;
import com.swiftdrop.parcel.support.AbstractParcelDatabaseTest;
import com.swiftdrop.parcel.support.ParcelFixtures;
import java.time.OffsetDateTime;
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

/**
 * FR-PAR-013 (TST-028, S). Test data is placed in March 2020 so it never mixes with parcels that other tests
 * create with the current time. Asia/Colombo is UTC+05:30: 18:45 UTC is already the next Colombo day.
 */
@Tag("TST-028")
class AdminReportApiTests extends AbstractParcelDatabaseTest {

    private static final String SUMMARY = "/api/v1/admin/reports/summary";
    private static final String DAILY = "/api/v1/admin/reports/daily";
    private static final String DASHBOARD = "/api/v1/admin/dashboard/parcels";

    @Autowired MockMvc mvc;
    @Autowired ParcelRepository parcels;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    private final List<UUID> created = new ArrayList<>();
    private final UUID customerId = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        created.forEach(id -> jdbc.update("DELETE FROM parcels WHERE id = ?", id));
        created.clear();
    }

    /** Inserts a parcel with chosen UTC timestamps (terminalAt may be null for a non-terminal status). */
    private void parcel(ParcelStatus status, String createdAtUtc, String terminalAtUtc) {
        String tracking = "SD" + ThreadLocalRandom.current().nextLong(10_000_000_000L, 99_999_999_999L);
        Parcel saved = parcels.saveAndFlush(ParcelFixtures.parcel(tracking, customerId, null, null));
        created.add(saved.getId());
        jdbc.update("UPDATE parcels SET status = ?, created_at = ?, terminal_at = ? WHERE id = ?", status.name(),
                OffsetDateTime.parse(createdAtUtc), terminalAtUtc == null ? null : OffsetDateTime.parse(terminalAtUtc),
                saved.getId());
    }

    private ResultActions admin(String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", TOKENS.admin().bearer()));
    }

    private void dataSet() {
        parcel(ParcelStatus.DELIVERED, "2020-03-09T05:00:00Z", "2020-03-10T18:45:00Z"); // delivered on Colombo 03-11
        parcel(ParcelStatus.FAILED, "2020-03-10T04:00:00Z", "2020-03-10T10:00:00Z");
        parcel(ParcelStatus.CANCELLED, "2020-03-10T05:00:00Z", "2020-03-10T12:00:00Z");
        parcel(ParcelStatus.CREATED, "2020-03-11T02:00:00Z", null); // in progress
        parcel(ParcelStatus.DELIVERED, "2020-04-20T05:00:00Z", "2020-04-21T05:00:00Z"); // outside the range
    }

    @Test
    @DisplayName("TST-028 FR-PAR-013: summary counts by creation day and terminal day with one-decimal rates")
    void summary() throws Exception {
        dataSet();

        admin(SUMMARY + "?from=2020-03-09&to=2020-03-11").andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2020-03-09"))
                .andExpect(jsonPath("$.created").value(4))
                .andExpect(jsonPath("$.delivered").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.cancelled").value(1))
                .andExpect(jsonPath("$.inProgress").value(1))
                .andExpect(jsonPath("$.successRate").value(50.0))
                .andExpect(jsonPath("$.failureRate").value(50.0));
    }

    @Test
    @DisplayName("TST-028 FR-PAR-013: rates round to one decimal and are 0 when nothing finished")
    void rates() throws Exception {
        parcel(ParcelStatus.DELIVERED, "2020-06-01T05:00:00Z", "2020-06-01T06:00:00Z");
        parcel(ParcelStatus.DELIVERED, "2020-06-01T05:00:00Z", "2020-06-01T07:00:00Z");
        parcel(ParcelStatus.FAILED, "2020-06-01T05:00:00Z", "2020-06-01T08:00:00Z");

        admin(SUMMARY + "?from=2020-06-01&to=2020-06-01")
                .andExpect(jsonPath("$.successRate").value(66.7)).andExpect(jsonPath("$.failureRate").value(33.3));
        admin(SUMMARY + "?from=2020-07-01&to=2020-07-02")
                .andExpect(jsonPath("$.successRate").value(0.0)).andExpect(jsonPath("$.failureRate").value(0.0))
                .andExpect(jsonPath("$.created").value(0));
    }

    @Test
    @DisplayName("TST-028 FR-PAR-013 BR-18: daily rows follow Colombo days; 18:45 UTC belongs to the next day; zero days included")
    void daily() throws Exception {
        dataSet();

        JsonNode rows = json.readTree(admin(DAILY + "?from=2020-03-09&to=2020-03-12").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(rows).hasSize(4);
        assertRow(rows.get(0), "2020-03-09", 1, 0, 0, 0);
        assertRow(rows.get(1), "2020-03-10", 2, 0, 1, 1);
        assertRow(rows.get(2), "2020-03-11", 1, 1, 0, 0);
        assertRow(rows.get(3), "2020-03-12", 0, 0, 0, 0);
    }

    private static void assertRow(JsonNode row, String date, int created, int delivered, int failed, int cancelled) {
        assertThat(row.get("date").asText()).isEqualTo(date);
        assertThat(row.get("created").asInt()).as(date + " created").isEqualTo(created);
        assertThat(row.get("delivered").asInt()).as(date + " delivered").isEqualTo(delivered);
        assertThat(row.get("failed").asInt()).as(date + " failed").isEqualTo(failed);
        assertThat(row.get("cancelled").asInt()).as(date + " cancelled").isEqualTo(cancelled);
    }

    @Test
    @DisplayName("TST-028 FR-PAR-013: default range is 30 days, 366 days is allowed, more or reversed gives 400")
    void ranges() throws Exception {
        admin(DAILY).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(30));
        admin(DAILY + "?from=2019-03-11&to=2020-03-10").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(366));
        admin(DAILY + "?from=2019-03-10&to=2020-03-10").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        admin(SUMMARY + "?from=2020-03-11&to=2020-03-10").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TST-028 FR-PAR-013: dashboard counts parcels finished today")
    void dashboard() throws Exception {
        JsonNode before = json.readTree(admin(DASHBOARD).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
        String now = OffsetDateTime.now().toString();
        parcel(ParcelStatus.DELIVERED, now, now);
        parcel(ParcelStatus.DELIVERED, now, now);
        parcel(ParcelStatus.FAILED, now, now);

        admin(DASHBOARD).andExpect(jsonPath("$.deliveredToday").value(before.get("deliveredToday").asLong() + 2))
                .andExpect(jsonPath("$.failedToday").value(before.get("failedToday").asLong() + 1));
    }

    @Test
    @Tag("TST-033")
    @DisplayName("TST-033 BR-25: report endpoints give 401 without a token and 403 to other roles and client tokens")
    void authorisation() throws Exception {
        UUID mid = UUID.randomUUID();
        List<String> bearers = List.of(TOKENS.customer().bearer(), TOKENS.merchant(mid).bearer(),
                TOKENS.courier().bearer(), TOKENS.client(mid, "orders:read", "orders:write").bearer());
        for (String url : List.of(SUMMARY, DAILY, DASHBOARD)) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            for (String bearer : bearers) {
                mvc.perform(get(url).header("Authorization", bearer)).andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            }
        }
    }
}

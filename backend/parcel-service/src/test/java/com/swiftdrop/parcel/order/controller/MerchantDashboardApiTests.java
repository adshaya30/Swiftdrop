package com.swiftdrop.parcel.order.controller;

import static com.swiftdrop.parcel.support.ParcelTestConfiguration.TOKENS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.parcel.order.AbstractMerchantOrderTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

@Tag("TST-032")
class MerchantDashboardApiTests extends AbstractMerchantOrderTest {

    private static final String SUMMARY = "/api/v1/merchant/dashboard/summary";

    @Test
    @DisplayName("FR-PAR-014: counts active orders per status and last-30-day orders per status, zeros included")
    void counts() throws Exception {
        UUID created = newOrder(mid, "D-1");
        UUID pickedUp = newOrder(mid, "D-2");
        UUID delivered = newOrder(mid, "D-3");
        UUID oldInTransit = newOrder(mid, "D-4");
        newOrder(otherMid, "D-OTHER");
        setStatus(pickedUp, ParcelStatus.PICKED_UP);
        setStatus(delivered, ParcelStatus.DELIVERED);
        setStatus(oldInTransit, ParcelStatus.IN_TRANSIT);
        createdAt(oldInTransit, Instant.now().minus(Duration.ofDays(40)));

        summary(TOKENS.merchant(mid).bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCounts.CREATED").value(1))
                .andExpect(jsonPath("$.activeCounts.PICKED_UP").value(1))
                .andExpect(jsonPath("$.activeCounts.IN_TRANSIT").value(1))
                .andExpect(jsonPath("$.activeCounts.OUT_FOR_DELIVERY").value(0))
                .andExpect(jsonPath("$.activeCounts.DELIVERED").doesNotExist())
                .andExpect(jsonPath("$.last30DaysCounts.CREATED").value(1))
                .andExpect(jsonPath("$.last30DaysCounts.PICKED_UP").value(1))
                .andExpect(jsonPath("$.last30DaysCounts.DELIVERED").value(1))
                .andExpect(jsonPath("$.last30DaysCounts.IN_TRANSIT").value(0))
                .andExpect(jsonPath("$.last30DaysCounts.FAILED").value(0))
                .andExpect(jsonPath("$.last30DaysCounts.CANCELLED").value(0))
                .andExpect(jsonPath("$.last30DaysCounts.OUT_FOR_DELIVERY").value(0))
                .andExpect(jsonPath("$.recentOrders.length()").value(4))
                .andExpect(jsonPath("$.recentOrders[?(@.merchantReference == 'D-OTHER')]").isEmpty());
        // The created order is listed with its fields.
        summary(TOKENS.merchant(mid).bearer())
                .andExpect(jsonPath("$.recentOrders[?(@.id == '" + created + "')].dropoffCity").value("Jaffna"))
                .andExpect(jsonPath("$.recentOrders[?(@.id == '" + created + "')].trackingNumber").exists());
    }

    @Test
    @DisplayName("FR-PAR-014: at most the 10 most recent orders, newest first")
    void recentOrders() throws Exception {
        List<UUID> ids = new ArrayList<>();
        Instant base = Instant.now().minus(Duration.ofHours(12));
        for (int i = 0; i < 12; i++) {
            UUID id = newOrder(mid, "R-" + i);
            createdAt(id, base.plus(Duration.ofMinutes(i)));
            ids.add(id);
        }

        summary(TOKENS.merchant(mid).bearer())
                .andExpect(jsonPath("$.recentOrders.length()").value(10))
                .andExpect(jsonPath("$.recentOrders[0].id").value(ids.get(11).toString()))
                .andExpect(jsonPath("$.recentOrders[0].merchantReference").value("R-11"))
                .andExpect(jsonPath("$.recentOrders[9].id").value(ids.get(2).toString()));
    }

    @Test
    @DisplayName("FR-PAR-014: a merchant without orders gets zeros and an empty list")
    void empty() throws Exception {
        summary(TOKENS.merchant(UUID.randomUUID()).bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCounts.CREATED").value(0))
                .andExpect(jsonPath("$.last30DaysCounts.DELIVERED").value(0))
                .andExpect(jsonPath("$.recentOrders").isEmpty());
    }

    @Test
    @DisplayName("TST-032 FR-PAR-014: client tokens get 403 (dashboard login only); other roles 403; no token 401")
    void access() throws Exception {
        summary(TOKENS.client(mid, "orders:read", "orders:write").bearer()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        summary(TOKENS.customer().bearer()).andExpect(status().isForbidden());
        summary(TOKENS.admin().bearer()).andExpect(status().isForbidden());
        summary(TOKENS.courier().bearer()).andExpect(status().isForbidden());
        mvc.perform(get(SUMMARY)).andExpect(status().isUnauthorized());
    }

    private ResultActions summary(String bearer) throws Exception {
        return mvc.perform(get(SUMMARY).header("Authorization", bearer));
    }

    private void createdAt(UUID id, Instant at) {
        jdbc.update("UPDATE parcels SET created_at = ? WHERE id = ?", Timestamp.from(at), id);
    }
}

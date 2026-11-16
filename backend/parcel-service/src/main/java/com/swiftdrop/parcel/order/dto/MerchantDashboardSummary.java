package com.swiftdrop.parcel.order.dto;

import com.swiftdrop.common.enums.ParcelStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FR-PAR-014 (shape decided in the member prompt, SRS gives only the content): activeCounts = all the merchant's
 * orders in the four active statuses; last30DaysCounts = orders created in the last 30 days, per status (all seven,
 * zero included); recentOrders = the 10 most recently created orders.
 */
public record MerchantDashboardSummary(Map<ParcelStatus, Long> activeCounts,
                                       Map<ParcelStatus, Long> last30DaysCounts,
                                       List<RecentOrder> recentOrders) {

    public record RecentOrder(UUID id, String trackingNumber, ParcelStatus status, String merchantReference,
                              String dropoffCity, Instant createdAt) {
    }
}

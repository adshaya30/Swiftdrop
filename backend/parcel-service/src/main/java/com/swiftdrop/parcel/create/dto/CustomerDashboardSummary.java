package com.swiftdrop.parcel.create.dto;

import java.util.List;

/**
 * FR-PAR-015: Customer dashboard summary.
 * Returns counts of active and delivered parcels and the five most recent parcels.
 */
public record CustomerDashboardSummary(
        long activeCount,
        long deliveredCount,
        List<ParcelDetailDto> recentParcels) {
}


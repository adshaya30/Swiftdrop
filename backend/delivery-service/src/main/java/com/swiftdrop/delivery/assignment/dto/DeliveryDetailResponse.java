package com.swiftdrop.delivery.assignment.dto;

import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.enums.VehicleType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeliveryDetailResponse(
        UUID parcelId,
        String trackingNumber,
        ParcelStatus status,
        CourierSummary courier,
        Instant assignedAt,
        List<AssignmentHistoryItem> assignments) {

    public record CourierSummary(UUID id, String fullName, VehicleType vehicleType, String vehicleNumber) {
    }

    public record AssignmentHistoryItem(
            UUID courierId,
            String courierName,
            UUID assignedBy,
            Instant assignedAt,
            Instant unassignedAt) {
    }
}
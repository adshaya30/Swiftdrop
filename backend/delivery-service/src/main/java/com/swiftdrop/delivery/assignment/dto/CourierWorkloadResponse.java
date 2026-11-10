package com.swiftdrop.delivery.assignment.dto;

import com.swiftdrop.common.enums.UserStatus;
import com.swiftdrop.common.enums.VehicleType;
import java.util.Map;
import java.util.UUID;

public record CourierWorkloadResponse(
        UUID id,
        String fullName,
        VehicleType vehicleType,
        String vehicleNumber,
        UserStatus status,
        Map<String, Long> activeDeliveries,
        long deliveredToday,
        long failedToday) {
}
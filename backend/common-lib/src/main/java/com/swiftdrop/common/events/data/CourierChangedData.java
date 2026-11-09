package com.swiftdrop.common.events.data;

import com.swiftdrop.common.enums.UserStatus;
import com.swiftdrop.common.enums.VehicleType;
import java.util.UUID;

/** Data of {@code courier.changed} (SRS 3.5). The courier's full name is allowed staff data (BR-28). */
public record CourierChangedData(
        UUID courierId,
        String fullName,
        UserStatus status,
        VehicleType vehicleType,
        String vehicleNumber) {
}

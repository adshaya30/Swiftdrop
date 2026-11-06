package com.swiftdrop.auth.users.dto;

import java.util.UUID;

public record CourierProfileSummaryResponse(
        UUID id,
        String vehicleType,
        String vehicleNumber,
        String status
) {}

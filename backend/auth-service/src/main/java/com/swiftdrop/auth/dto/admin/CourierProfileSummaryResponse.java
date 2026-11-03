package com.swiftdrop.auth.dto.admin;

import java.util.UUID;

/**
 * Summary of courier profile data embedded in admin user responses.
 * SRS v2.1 – FR-AUTH-020
 */
public record CourierProfileSummaryResponse(
        UUID id,
        String vehicleType,
        String vehicleNumber,
        String status
) {}


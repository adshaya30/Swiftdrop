package com.swiftdrop.auth.dto.admin;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for modifying permitted user details by an administrator.
 * SRS v2.1 – FR-AUTH-020
 */
public record EditUserRequest(
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        @Pattern(regexp = "^(\\+94[0-9]{9})?$", message = "Phone must be in Sri Lankan format (+94XXXXXXXXX) or empty")
        String phone,

        @Size(max = 50, message = "Vehicle type must not exceed 50 characters")
        String vehicleType,

        @Size(max = 30, message = "Vehicle number must not exceed 30 characters")
        String vehicleNumber
) {}


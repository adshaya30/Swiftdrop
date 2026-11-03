package com.swiftdrop.auth.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating a new user (ADMIN or COURIER) by an administrator.
 * SRS v2.1 – FR-AUTH-020
 */
public record CreateUserRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,

        @Pattern(regexp = "^(\\+94[0-9]{9})?$", message = "Phone must be in Sri Lankan format (+94XXXXXXXXX) or empty")
        String phone,

        @NotBlank(message = "Role is required (ADMIN or COURIER)")
        String role,

        String vehicleType,

        String vehicleNumber
) {}


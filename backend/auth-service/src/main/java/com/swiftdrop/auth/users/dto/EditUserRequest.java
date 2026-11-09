package com.swiftdrop.auth.users.dto;

import jakarta.validation.constraints.Size;

public record EditUserRequest(
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        String phone,

        @Size(max = 50, message = "Vehicle type must not exceed 50 characters")
        String vehicleType,

        @Size(max = 30, message = "Vehicle number must not exceed 30 characters")
        String vehicleNumber
) {}


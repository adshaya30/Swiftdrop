package com.swiftdrop.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for updating user profile.
 * Only fullName and phone are modifiable. Direct JPA binding avoided.
 * FR-AUTH-012
 */
public record UpdateProfileRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        @Pattern(regexp = "^(\\+94[0-9]{9})?$", message = "Phone must be in Sri Lankan format (+94XXXXXXXXX) or empty")
        String phone
) {}

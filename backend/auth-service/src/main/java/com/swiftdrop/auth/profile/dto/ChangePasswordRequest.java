package com.swiftdrop.auth.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for changing user password.
 * FR-AUTH-013
 */
public record ChangePasswordRequest(
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 10, max = 64, message = "New password must be between 10 and 64 characters")
        String newPassword
) {}

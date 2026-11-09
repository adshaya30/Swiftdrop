package com.swiftdrop.auth.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/v1/merchant/credentials (FR-AUTH-018). */
public record CreateCredentialRequest(
        @NotBlank(message = "Label is required")
        @Size(min = 1, max = 50, message = "Label must be between 1 and 50 characters")
        String label) {
}

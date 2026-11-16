package com.swiftdrop.auth.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST .../reject and POST .../suspend: a reason of 10-300 characters (BR-12, FR-AUTH-017). */
public record StatusReasonRequest(
        @NotBlank(message = "Reason is required")
        @Size(min = 10, max = 300, message = "Reason must be between 10 and 300 characters")
        String reason) {
}

package com.swiftdrop.auth.merchant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/v1/auth/register-merchant.
 * SRS v2.1 - FR-AUTH-016. Unknown JSON properties are rejected with 400 (master prompt section 6).
 */
public record RegisterMerchantRequest(

        @NotBlank(message = "Business name is required")
        @Size(min = 2, max = 120, message = "Business name must be between 2 and 120 characters")
        String businessName,

        @Size(max = 40, message = "Business registration number must not exceed 40 characters")
        String businessRegistrationNo,

        @NotBlank(message = "Contact name is required")
        @Size(min = 2, max = 100, message = "Contact name must be between 2 and 100 characters")
        String contactName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,

        @NotBlank(message = "Phone number is required")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 10, max = 64, message = "Password must be between 10 and 64 characters")
        String password,

        @NotBlank(message = "Address is required")
        @Size(min = 1, max = 150, message = "Address must be between 1 and 150 characters")
        String addressLine1,

        @NotBlank(message = "City is required")
        @Size(min = 1, max = 80, message = "City must be between 1 and 80 characters")
        String city) {

    /** Keeps the password out of logs and exception messages. */
    @Override
    public String toString() {
        return "RegisterMerchantRequest[businessName=" + businessName + ", password=****]";
    }
}

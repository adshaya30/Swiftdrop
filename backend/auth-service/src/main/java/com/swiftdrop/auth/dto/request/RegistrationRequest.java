package com.swiftdrop.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

/**
 * Registration request DTO.
 * SRS v2.1 – FR-AUTH-001
 *
 * <p>Jackson is globally configured with
 * {@code DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = true} so any
 * extra field (e.g., "role") causes a 400 before reaching this class.</p>
 */
public class RegistrationRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    @JsonProperty("fullName")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 254, message = "Email must not exceed 254 characters")
    @JsonProperty("email")
    private String email;

    /**
     * Accepts LK phone numbers in multiple formats; service normalises to E.164.
     * Example accepted: 0771234567, +94771234567, 077-123-4567
     */
    @NotBlank(message = "Phone number is required")
    @JsonProperty("phone")
    private String phone;

    /**
     * 10–64 characters with complexity requirements.
     * Pattern validated in service layer (requires email local-part check).
     */
    @NotBlank(message = "Password is required")
    @Size(min = 10, max = 64, message = "Password must be between 10 and 64 characters")
    @JsonProperty("password")
    private String password;

    public RegistrationRequest() {}

    public RegistrationRequest(String fullName, String email, String phone, String password) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.password = password;
    }

    // -----------------------------------------------------------------------
    // Getters & setters
    // -----------------------------------------------------------------------

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}

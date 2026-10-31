package com.swiftdrop.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Safe user summary returned after successful registration.
 * SRS v2.1 – FR-AUTH-001
 *
 * <p>Never includes password, passwordHash, or tokens.</p>
 */
public class UserResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("fullName")
    private String fullName;

    @JsonProperty("email")
    private String email;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("role")
    private String role;

    @JsonProperty("authProvider")
    private String authProvider;

    @JsonProperty("status")
    private String status;

    @JsonProperty("createdAt")
    private Instant createdAt;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    public UserResponse() {}

    public UserResponse(UUID id, String fullName, String email, String phone,
                        String role, String authProvider, String status,
                        Instant createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.authProvider = authProvider;
        this.status = status;
        this.createdAt = createdAt;
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getRole() { return role; }
    public String getAuthProvider() { return authProvider; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}

package com.swiftdrop.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * User summary included in successful login response.
 * SRS v2.1 – FR-AUTH-003
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginUserSummary {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("fullName")
    private String fullName;

    @JsonProperty("role")
    private String role;

    @JsonProperty("mustChangePassword")
    private boolean mustChangePassword;

    @JsonProperty("merchantStatus")
    private String merchantStatus;

    @JsonProperty("merchantId")
    private UUID merchantId;

    public LoginUserSummary() {}

    public LoginUserSummary(UUID id, String fullName, String role, boolean mustChangePassword) {
        this(id, fullName, role, mustChangePassword, null, null);
    }

    public LoginUserSummary(UUID id, String fullName, String role, boolean mustChangePassword,
                            String merchantStatus, UUID merchantId) {
        this.id = id;
        this.fullName = fullName;
        this.role = role;
        this.mustChangePassword = mustChangePassword;
        this.merchantStatus = merchantStatus;
        this.merchantId = merchantId;
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public String getMerchantStatus() { return merchantStatus; }
    public UUID getMerchantId() { return merchantId; }
}


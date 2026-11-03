package com.swiftdrop.auth.dto.admin;

import com.swiftdrop.auth.dto.response.MerchantSummaryResponse;
import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Detailed user response for admin management views.
 * Excludes sensitive fields (passwords, hashes, refresh tokens, secrets).
 * SRS v2.1 – FR-AUTH-020
 */
public record AdminUserSummaryResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        UserRole role,
        UserStatus status,
        AuthProvider authProvider,
        boolean mustChangePassword,
        int failedLoginCount,
        Instant lockedUntil,
        Instant lastLoginAt,
        Instant createdAt,
        CourierProfileSummaryResponse courierProfile,
        MerchantSummaryResponse merchant
) {}


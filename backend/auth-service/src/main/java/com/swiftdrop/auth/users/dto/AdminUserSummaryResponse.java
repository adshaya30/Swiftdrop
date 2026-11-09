package com.swiftdrop.auth.users.dto;

import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.entity.UserRole;
import com.swiftdrop.auth.entity.UserStatus;
import com.swiftdrop.auth.profile.dto.MerchantSummaryResponse;

import java.time.Instant;
import java.util.UUID;

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


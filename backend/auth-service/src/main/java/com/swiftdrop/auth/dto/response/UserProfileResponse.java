package com.swiftdrop.auth.dto.response;

import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.entity.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Detailed profile response for the authenticated user.
 * FR-AUTH-012
 */
public record UserProfileResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        UserRole role,
        AuthProvider authProvider,
        boolean mustChangePassword,
        Instant createdAt,
        MerchantSummaryResponse merchant
) {}

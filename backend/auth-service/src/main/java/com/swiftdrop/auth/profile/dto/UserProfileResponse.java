package com.swiftdrop.auth.profile.dto;

import com.swiftdrop.auth.entity.AuthProvider;
import com.swiftdrop.auth.entity.UserRole;

import java.time.Instant;
import java.util.UUID;

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

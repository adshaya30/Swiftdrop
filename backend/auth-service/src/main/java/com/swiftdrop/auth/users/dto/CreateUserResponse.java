package com.swiftdrop.auth.users.dto;

public record CreateUserResponse(
        AdminUserSummaryResponse user,
        String temporaryPassword
) {}

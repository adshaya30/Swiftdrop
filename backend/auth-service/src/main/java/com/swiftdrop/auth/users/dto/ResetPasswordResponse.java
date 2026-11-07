package com.swiftdrop.auth.users.dto;

public record ResetPasswordResponse(
        String temporaryPassword,
        String message
) {}


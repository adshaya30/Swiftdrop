package com.swiftdrop.auth.dto.admin;

/**
 * Response payload returned upon successful administrative password reset.
 * Contains the generated 16-character temporary password shown exactly once.
 * SRS v2.1 – FR-AUTH-021
 */
public record ResetPasswordResponse(
        String temporaryPassword,
        String message
) {}


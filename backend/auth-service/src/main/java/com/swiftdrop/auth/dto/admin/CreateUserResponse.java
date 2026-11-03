package com.swiftdrop.auth.dto.admin;

/**
 * Response payload returned upon successful creation of an ADMIN or COURIER.
 * Contains the generated 16-character temporary password shown exactly once.
 * SRS v2.1 – FR-AUTH-020
 */
public record CreateUserResponse(
        AdminUserSummaryResponse user,
        String temporaryPassword
) {}


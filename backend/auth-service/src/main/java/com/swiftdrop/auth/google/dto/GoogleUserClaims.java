package com.swiftdrop.auth.google.dto;

public record GoogleUserClaims(
        String sub,
        String email,
        boolean emailVerified,
        String name,
        String picture
) {}

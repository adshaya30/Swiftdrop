package com.swiftdrop.common.security;

/** Claim names and fixed values of SwiftDrop access tokens (SRS 3.6). */
public final class JwtClaims {
    public static final String ISSUER = "swiftdrop";
    public static final String AUDIENCE = "swiftdrop-api";
    public static final String ROLE = "role";
    public static final String TYP = "typ";
    public static final String MID = "mid";
    public static final String SCOPE = "scope";
    public static final String PWC = "pwc";
    public static final String TYP_USER = "user";
    public static final String TYP_CLIENT = "client";
    public static final long CLOCK_SKEW_SECONDS = 30;

    private JwtClaims() {
    }
}

package com.swiftdrop.auth.exception;

/**
 * Thrown when a refresh token is missing, invalid, expired, revoked, or replayed.
 * Maps to HTTP 401 with code UNAUTHENTICATED.
 * SRS v2.1 – FR-AUTH-007, Section 3.6
 */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }

    public UnauthenticatedException() {
        super("Authentication is required to access this resource");
    }
}


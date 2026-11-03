package com.swiftdrop.auth.exception;

import java.util.UUID;

/**
 * Thrown when a user record is not found in auth-service.
 * FR-AUTH-012
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID userId) {
        super("User not found: " + userId);
    }

    public UserNotFoundException(String message) {
        super(message);
    }
}

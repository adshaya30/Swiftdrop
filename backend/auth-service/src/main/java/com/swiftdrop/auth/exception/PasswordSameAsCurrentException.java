package com.swiftdrop.auth.exception;

/**
 * Thrown when the new password is the same as the current password.
 * FR-AUTH-013
 */
public class PasswordSameAsCurrentException extends RuntimeException {
    public PasswordSameAsCurrentException() {
        super("New password must be different from current password.");
    }
}

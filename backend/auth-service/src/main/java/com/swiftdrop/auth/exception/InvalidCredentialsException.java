package com.swiftdrop.auth.exception;

/**
 * Thrown when email/password login credentials do not match or account has no password.
 * Always returns 401 INVALID_CREDENTIALS with safe message "Invalid email or password".
 * SRS v2.1 – FR-AUTH-004
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}


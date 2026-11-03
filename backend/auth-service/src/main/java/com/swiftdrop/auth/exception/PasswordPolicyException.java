package com.swiftdrop.auth.exception;

/**
 * Thrown when a password fails complexity requirements or contains the email local-part.
 * SRS v2.1 – FR-AUTH-001
 */
public class PasswordPolicyException extends RuntimeException {

    public PasswordPolicyException(String message) {
        super(message);
    }
}

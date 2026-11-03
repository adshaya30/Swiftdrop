package com.swiftdrop.auth.exception;

/**
 * Thrown when a registration attempt uses an email address already registered.
 * SRS v2.1 – FR-AUTH-001, Error code EMAIL_ALREADY_REGISTERED
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("Email address is already registered: " + email);
    }
}

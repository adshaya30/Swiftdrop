package com.swiftdrop.auth.exception;

/**
 * Thrown when a phone number cannot be normalised to a valid Sri Lankan E.164 number.
 * SRS v2.1 – FR-AUTH-001
 */
public class InvalidPhoneException extends RuntimeException {

    public InvalidPhoneException(String phone) {
        super("Invalid or unsupported Sri Lankan phone number: " + phone);
    }
}

package com.swiftdrop.auth.exception;

/**
 * Thrown when the provided current password does not match the stored hash.
 * FR-AUTH-013
 */
public class InvalidCurrentPasswordException extends RuntimeException {
    public InvalidCurrentPasswordException() {
        super("Current password is incorrect.");
    }
}

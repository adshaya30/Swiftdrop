package com.swiftdrop.auth.exception;

/**
 * Thrown when a required header (such as X-Requested-With: SwiftDrop) is missing or invalid.
 * Maps to HTTP 400 Bad Request with code INVALID_REQUEST.
 * SRS v2.1 – FR-AUTH-007, FR-AUTH-008
 */
public class InvalidHeaderException extends RuntimeException {

    public InvalidHeaderException(String message) {
        super(message);
    }
}


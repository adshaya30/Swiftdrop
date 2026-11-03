package com.swiftdrop.auth.exception;

/**
 * Thrown when an invalid status transition is requested (e.g. activating an already active user).
 * SRS v2.1 – FR-AUTH-020
 */
public class InvalidUserStatusTransitionException extends RuntimeException {
    public InvalidUserStatusTransitionException(String message) {
        super(message);
    }
}


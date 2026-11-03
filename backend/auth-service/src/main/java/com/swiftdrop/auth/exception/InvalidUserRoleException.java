package com.swiftdrop.auth.exception;

/**
 * Thrown when an invalid role is requested for creation via admin user management.
 * SRS v2.1 – FR-AUTH-020
 */
public class InvalidUserRoleException extends RuntimeException {
    public InvalidUserRoleException(String message) {
        super(message);
    }
}


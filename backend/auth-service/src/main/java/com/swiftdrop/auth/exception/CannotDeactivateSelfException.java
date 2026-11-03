package com.swiftdrop.auth.exception;

/**
 * Thrown when an administrator attempts to deactivate their own account.
 * SRS v2.1 – FR-AUTH-020
 */
public class CannotDeactivateSelfException extends RuntimeException {
    public CannotDeactivateSelfException() {
        super("Administrators cannot deactivate their own account.");
    }
}


package com.swiftdrop.auth.exception;

/**
 * Thrown when attempting to change the password of an OAuth-only/social account.
 * FR-AUTH-013
 */
public class SocialAccountPasswordChangeException extends RuntimeException {
    public SocialAccountPasswordChangeException() {
        super("Social login accounts do not have a local password to change.");
    }
}

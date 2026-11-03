package com.swiftdrop.auth.exception;

/**
 * Thrown when credentials are valid but the account has DEACTIVATED status.
 * Returns 403 ACCOUNT_DEACTIVATED.
 * SRS v2.1 – FR-AUTH-004
 */
public class AccountDeactivatedException extends RuntimeException {
    public AccountDeactivatedException() {
        super("Account is deactivated.");
    }
}


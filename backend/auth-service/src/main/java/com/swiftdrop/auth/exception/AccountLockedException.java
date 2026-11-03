package com.swiftdrop.auth.exception;

/**
 * Thrown when an account is locked after 5 consecutive failed login attempts.
 * Returns 429 ACCOUNT_LOCKED with Retry-After header.
 * SRS v2.1 – FR-AUTH-005
 */
public class AccountLockedException extends RuntimeException {

    private final long retryAfterSeconds;

    public AccountLockedException(long retryAfterSeconds) {
        super("Account is locked due to multiple failed login attempts. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}


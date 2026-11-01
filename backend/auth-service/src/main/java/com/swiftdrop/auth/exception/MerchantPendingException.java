package com.swiftdrop.auth.exception;

/**
 * Thrown when credentials are valid but the merchant account status is PENDING.
 * Returns 403 MERCHANT_PENDING.
 * SRS v2.1 – FR-AUTH-004
 */
public class MerchantPendingException extends RuntimeException {
    public MerchantPendingException() {
        super("Merchant account is pending approval.");
    }
}


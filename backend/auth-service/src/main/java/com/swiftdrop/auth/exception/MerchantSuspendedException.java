package com.swiftdrop.auth.exception;

/**
 * Thrown when credentials are valid but the merchant account status is SUSPENDED.
 * Returns 403 MERCHANT_SUSPENDED without revealing suspension reason in the response.
 * SRS v2.1 – FR-AUTH-004
 */
public class MerchantSuspendedException extends RuntimeException {
    public MerchantSuspendedException() {
        super("Merchant account is suspended.");
    }
}


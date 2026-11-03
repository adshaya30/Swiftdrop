package com.swiftdrop.auth.exception;

/**
 * Thrown when credentials are valid but the merchant account status is REJECTED.
 * Returns 403 MERCHANT_REJECTED without revealing rejection reason in the response.
 * SRS v2.1 – FR-AUTH-004
 */
public class MerchantRejectedException extends RuntimeException {
    public MerchantRejectedException() {
        super("Merchant account was rejected.");
    }
}


package com.swiftdrop.auth.merchant.dto;

/** 201 body of POST /api/v1/auth/register-merchant: a message only, never tokens (FR-AUTH-016). */
public record RegisterMerchantResponse(String message) {

    public static final String PENDING_MESSAGE =
            "Your registration is waiting for admin approval. You will be able to log in once it is approved.";

    public static RegisterMerchantResponse pending() {
        return new RegisterMerchantResponse(PENDING_MESSAGE);
    }
}

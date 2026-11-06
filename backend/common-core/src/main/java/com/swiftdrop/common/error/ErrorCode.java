package com.swiftdrop.common.error;

/** All error codes of SRS 7.2 with their HTTP status and default title. */
public enum ErrorCode {
    VALIDATION_ERROR(400, "Validation failed"),

    UNAUTHENTICATED(401, "Authentication required"),
    TOKEN_EXPIRED(401, "Token expired"),
    INVALID_CREDENTIALS(401, "Invalid credentials"),

    ACCESS_DENIED(403, "Access denied"),
    PASSWORD_CHANGE_REQUIRED(403, "Password change required"),
    ACCOUNT_DEACTIVATED(403, "Account deactivated"),
    MERCHANT_PENDING(403, "Merchant pending approval"),
    MERCHANT_REJECTED(403, "Merchant rejected"),
    MERCHANT_SUSPENDED(403, "Merchant suspended"),
    MERCHANT_NOT_ACTIVE(403, "Merchant not active"),

    NOT_FOUND(404, "Resource not found"),

    EMAIL_ALREADY_REGISTERED(409, "Email already registered"),
    FEE_CHANGED(409, "Fee changed"),
    INVALID_STATUS_TRANSITION(409, "Invalid status transition"),
    PARCEL_NOT_ASSIGNABLE(409, "Parcel not assignable"),
    DUPLICATE_MERCHANT_REFERENCE(409, "Duplicate merchant reference"),
    CONCURRENT_UPDATE(409, "Concurrent update"),
    COURIER_HAS_ACTIVE_DELIVERIES(409, "Courier has active deliveries"),
    CREDENTIAL_LIMIT_REACHED(409, "Credential limit reached"),
    ZONE_IN_USE(409, "Zone in use"),

    PAYLOAD_TOO_LARGE(413, "Payload too large"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported media type"),

    IDEMPOTENCY_KEY_REUSED(422, "Idempotency key reused"),
    PRICING_NOT_CONFIGURED(422, "Pricing not configured"),
    ZONE_NOT_SERVICEABLE(422, "Zone not serviceable"),

    ACCOUNT_LOCKED(429, "Account locked"),
    RATE_LIMITED(429, "Too many requests"),

    INTERNAL_ERROR(500, "Internal error"),

    SERVICE_UNAVAILABLE(503, "Service unavailable"),
    ASSISTANT_UNAVAILABLE(503, "Assistant unavailable");

    private final int status;
    private final String title;

    ErrorCode(int status, String title) {
        this.status = status;
        this.title = title;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }
}

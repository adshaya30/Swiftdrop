package com.swiftdrop.common.http;

/** Header names used across services. */
public final class ApiHeaders {
    public static final String REQUEST_ID = "X-Request-Id";
    public static final String INTERNAL_KEY = "X-Internal-Key";
    public static final String REQUESTED_WITH = "X-Requested-With";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAY = "Idempotent-Replay";
    public static final String RETRY_AFTER = "Retry-After";

    private ApiHeaders() {
    }
}

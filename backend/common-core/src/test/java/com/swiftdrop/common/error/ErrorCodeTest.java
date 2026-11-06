package com.swiftdrop.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("TST-013")
class ErrorCodeTest {

    @Test
    @DisplayName("TST-013 SRS 7.2: error codes carry the specified HTTP status")
    void statuses() {
        assertThat(ErrorCode.VALIDATION_ERROR.status()).isEqualTo(400);
        assertThat(ErrorCode.TOKEN_EXPIRED.status()).isEqualTo(401);
        assertThat(ErrorCode.MERCHANT_SUSPENDED.status()).isEqualTo(403);
        assertThat(ErrorCode.NOT_FOUND.status()).isEqualTo(404);
        assertThat(ErrorCode.CREDENTIAL_LIMIT_REACHED.status()).isEqualTo(409);
        assertThat(ErrorCode.PAYLOAD_TOO_LARGE.status()).isEqualTo(413);
        assertThat(ErrorCode.UNSUPPORTED_MEDIA_TYPE.status()).isEqualTo(415);
        assertThat(ErrorCode.ZONE_NOT_SERVICEABLE.status()).isEqualTo(422);
        assertThat(ErrorCode.RATE_LIMITED.status()).isEqualTo(429);
        assertThat(ErrorCode.INTERNAL_ERROR.status()).isEqualTo(500);
        assertThat(ErrorCode.ASSISTANT_UNAVAILABLE.status()).isEqualTo(503);
    }

    @Test
    @DisplayName("TST-013 SRS 7.2: every code has a default title")
    void titles() {
        for (ErrorCode c : ErrorCode.values()) {
            assertThat(c.title()).as(c.name()).isNotBlank();
        }
    }

    @Test
    @DisplayName("TST-013 DomainException keeps code, detail, errors and extensions")
    void domainException() {
        var ex = new DomainException(ErrorCode.FEE_CHANGED, "Fee changed",
                List.of(new FieldError("expectedFee", "stale")), Map.of("fee", "500.00"));
        assertThat(ex.code()).isEqualTo(ErrorCode.FEE_CHANGED);
        assertThat(ex.detail()).isEqualTo("Fee changed");
        assertThat(ex.errors()).hasSize(1);
        assertThat(ex.extensions()).containsEntry("fee", "500.00");
        assertThat(new DomainException(ErrorCode.NOT_FOUND, "x").errors()).isEmpty();
    }

    @Test
    @DisplayName("TST-013 ProblemBody: type, status, code and extensions")
    void problemBody() {
        var body = ProblemBody.of(ErrorCode.INVALID_STATUS_TRANSITION, "no", "trace-1",
                null, Map.of("newFee", "10.00"));
        assertThat(body.type()).isEqualTo("https://swiftdrop.example/errors/invalid-status-transition");
        assertThat(body.status()).isEqualTo(409);
        assertThat(body.code()).isEqualTo("INVALID_STATUS_TRANSITION");
        assertThat(body.traceId()).isEqualTo("trace-1");
        assertThat(body.errors()).isNull();
        assertThat(body.anyExtensions()).containsEntry("newFee", "10.00");
        assertThat(body.timestamp()).isNotNull();
    }
}

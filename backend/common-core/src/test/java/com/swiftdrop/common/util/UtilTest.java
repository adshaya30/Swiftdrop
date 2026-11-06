package com.swiftdrop.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.common.http.ApiHeaders;
import com.swiftdrop.common.paging.Pagination;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("TST-013")
class UtilTest {

    @Test
    @DisplayName("TST-013 BR-28: masking of phone and email, null safe")
    void masking() {
        assertThat(MaskingUtil.maskPhone("+94771234567")).isEqualTo("+94*****4567");
        assertThat(MaskingUtil.maskPhone("123")).isEqualTo("****");
        assertThat(MaskingUtil.maskPhone(null)).isNull();
        assertThat(MaskingUtil.maskEmail("john@example.com")).isEqualTo("j***@example.com");
        assertThat(MaskingUtil.maskEmail("nonsense")).isEqualTo("***");
        assertThat(MaskingUtil.maskEmail(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0771234567", "+94771234567", "94771234567", "077 123 4567", "(077)-123-4567", "0112345678"})
    @DisplayName("TST-013 BR-11: valid Sri Lankan phone forms normalise to E.164")
    void phoneValid(String raw) {
        assertThat(PhoneNumberNormalizer.normalize(raw)).hasValueSatisfying(
                v -> assertThat(v).matches("^\\+94[1-9][0-9]{8}$"));
    }

    @Test
    @DisplayName("TST-013 BR-11: phone examples and invalid inputs")
    void phoneExamples() {
        assertThat(PhoneNumberNormalizer.normalize("0771234567")).contains("+94771234567");
        assertThat(PhoneNumberNormalizer.normalize("0112345678")).contains("+94112345678");
        assertThat(PhoneNumberNormalizer.normalize("12345")).isEmpty();
        assertThat(PhoneNumberNormalizer.normalize("+1 555 123 4567")).isEmpty();
        assertThat(PhoneNumberNormalizer.normalize("077123456")).isEmpty();
        assertThat(PhoneNumberNormalizer.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("TST-013 FR-PAR-005: tracking number format")
    void trackingNumber() {
        assertThat(TrackingNumberFormat.isValid("SD-0123456789")).isTrue();
        assertThat(TrackingNumberFormat.isValid("SD-ABCDEFGHJK")).isTrue();
        assertThat(TrackingNumberFormat.isValid("SD-ABCDEFGHIK")).isFalse(); // I not in alphabet
        assertThat(TrackingNumberFormat.isValid("SD-ABCDEFGHJU")).isFalse(); // U not in alphabet
        assertThat(TrackingNumberFormat.isValid("sd-0123456789")).isFalse();
        assertThat(TrackingNumberFormat.isValid("SD-012345678")).isFalse();
        assertThat(TrackingNumberFormat.isValid(null)).isFalse();
        assertThat(TrackingNumberFormat.ALPHABET).hasSize(32);
    }

    @Test
    @DisplayName("TST-013 pagination totals and header names")
    void paginationAndHeaders() {
        var p = Pagination.of(List.of(1, 2), 0, 20, 41);
        assertThat(p.totalPages()).isEqualTo(3);
        assertThat(Pagination.of(List.of(), 0, 20, 0).totalPages()).isZero();
        assertThat(ApiHeaders.REQUEST_ID).isEqualTo("X-Request-Id");
        assertThat(ApiHeaders.INTERNAL_KEY).isEqualTo("X-Internal-Key");
        assertThat(ApiHeaders.IDEMPOTENT_REPLAY).isEqualTo("Idempotent-Replay");
    }
}

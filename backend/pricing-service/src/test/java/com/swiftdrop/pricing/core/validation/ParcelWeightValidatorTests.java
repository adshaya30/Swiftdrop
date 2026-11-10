package com.swiftdrop.pricing.core.validation;

import com.swiftdrop.pricing.core.config.PricingProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("TST-010")
class ParcelWeightValidatorTests {
    private final ParcelWeightValidator validator = new ParcelWeightValidator(new PricingProperties(new BigDecimal("30")));

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1.00", "1.01", "2.00", "30.00"})
    @DisplayName("TST-010 FR-PRC-004: valid weight boundaries")
    void acceptsSupportedWeight(String weight) {
        assertThat(validator.isValid(new BigDecimal(weight), null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-1", "30.01", "2.001", "1.000", "0.001"})
    @DisplayName("TST-010 FR-PRC-004: reject minimum, precision and maximum violations")
    void rejectsInvalidWeight(String weight) {
        assertThat(validator.isValid(new BigDecimal(weight), null)).isFalse();
    }

    @Test
    @DisplayName("TST-010 FR-PRC-009: maximum is configurable and null is left to NotNull")
    void usesConfiguration() {
        var custom = new ParcelWeightValidator(new PricingProperties(new BigDecimal("45")));
        assertThat(custom.isValid(new BigDecimal("30.01"), null)).isTrue();
        assertThat(custom.isValid(new BigDecimal("45.01"), null)).isFalse();
        assertThat(custom.isValid(null, null)).isTrue();
    }
}

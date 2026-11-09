package com.swiftdrop.pricing.core.config;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class PricingPropertiesTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(PricingConfiguration.class);

    @Test
    @DisplayName("FR-PRC-009: bind a decimal configured maximum")
    void bindsMaximum() {
        runner.withPropertyValues("pricing.max-parcel-weight-kg=45.50").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(PricingProperties.class).maxParcelWeightKg()).isEqualByComparingTo(new BigDecimal("45.50"));
        });
    }

    @Test
    @DisplayName("FR-PRC-009: invalid and missing maximum configuration fail startup")
    void rejectsInvalidConfiguration() {
        for (String value : new String[]{"0", "-1", "not-a-number"}) {
            runner.withPropertyValues("pricing.max-parcel-weight-kg=" + value).run(context -> assertThat(context).hasFailed());
        }
        runner.run(context -> assertThat(context).hasFailed());
    }
}

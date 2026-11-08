package com.swiftdrop.pricing.core.validation;

import com.swiftdrop.pricing.core.config.PricingProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

/** BR-10 / FR-PRC-004: pricing alone enforces the configured maximum. */
public class ParcelWeightValidator implements ConstraintValidator<ValidParcelWeight, BigDecimal> {
    private static final BigDecimal MINIMUM = new BigDecimal("0.01");
    private final PricingProperties properties;

    public ParcelWeightValidator(PricingProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        // @NotNull supplies the required-field error independently.
        return value == null || (value.scale() <= 2 && value.compareTo(MINIMUM) >= 0
                && value.compareTo(properties.maxParcelWeightKg()) <= 0);
    }
}

package com.swiftdrop.parcel.create.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

public class WeightValidator implements ConstraintValidator<ValidWeight, BigDecimal> {
    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        return value == null || (value.compareTo(new BigDecimal("0.01")) >= 0 && value.scale() <= 2);
    }
}

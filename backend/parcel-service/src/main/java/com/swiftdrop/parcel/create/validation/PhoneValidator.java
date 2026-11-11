package com.swiftdrop.parcel.create.validation;

import com.swiftdrop.common.util.PhoneNumberNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PhoneValidator implements ConstraintValidator<ValidPhone, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || PhoneNumberNormalizer.normalize(value).isPresent();
    }
}

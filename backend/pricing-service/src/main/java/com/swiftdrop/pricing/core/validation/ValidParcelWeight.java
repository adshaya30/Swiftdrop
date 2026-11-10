package com.swiftdrop.pricing.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ParcelWeightValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidParcelWeight {
    String message() default "Weight must have at most two decimal places and be within the supported range";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

package com.swiftdrop.parcel.create.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** BR-10: minimum and decimal format only; pricing-service alone owns the maximum. */
@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = WeightValidator.class)
public @interface ValidWeight {
    String message() default "must be at least 0.01 kg with at most two decimal places";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

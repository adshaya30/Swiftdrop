package com.swiftdrop.parcel.create.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** FR-PAR-005: reuse the common Sri Lankan phone rules. */
@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = PhoneValidator.class)
public @interface ValidPhone {
    String message() default "must be a valid Sri Lankan phone number";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

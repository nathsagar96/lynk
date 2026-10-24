package ly.lynk.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Validates that a string is a well-formed HTTP or HTTPS URL. */
@Documented
@Constraint(validatedBy = HttpUrlValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface HttpUrl {

    /** Validation error message. */
    String message() default "Must be a valid HTTP or HTTPS URL";

    /** Validation groups. */
    Class<?>[] groups() default {};

    /** Payload metadata. */
    Class<? extends Payload>[] payload() default {};
}

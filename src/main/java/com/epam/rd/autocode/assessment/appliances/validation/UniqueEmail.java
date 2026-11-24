package com.epam.rd.autocode.assessment.appliances.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Class-level constraint for Client/Employee forms: the email must not be used by any other
 * client or employee (login looks in both tables, so it has to be unique across them).
 * The row being edited is ignored. The error is shown on the email field.
 *
 * The annotated class needs getters for the id and email properties.
 */
@Documented
@Constraint(validatedBy = UniqueEmailValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueEmail {

    String message() default "{validation.user.email.unique}";

    /** Whether the validated form is for a client or an employee. */
    UserType type();

    String idField() default "id";

    String emailField() default "email";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

package com.epam.rd.autocode.assessment.appliances.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Class-level constraint: the two password fields of a form must be equal.
 * The error is shown on the confirmation field.
 */
@Documented
@Constraint(validatedBy = PasswordsMatchValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordsMatch {

    String message() default "{validation.user.password.mismatch}";

    String passwordField() default "password";

    String confirmField() default "confirmPassword";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
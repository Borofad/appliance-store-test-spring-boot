package com.epam.rd.autocode.assessment.appliances.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Password policy: 8-64 characters with at least one uppercase letter, one lowercase letter,
 * one digit and one special character. Null/blank values are treated as valid, so combine with
 * @NotBlank when the password is required (and leave it off on "edit" forms where an empty
 * password means "keep the current one").
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "{validation.user.password.weak}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

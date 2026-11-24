package com.epam.rd.autocode.assessment.appliances.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Class-level constraint for the Manufacturer form: the name must be unique (ignoring case).
 * The row being edited is ignored. The error is shown on the name field.
 */
@Documented
@Constraint(validatedBy = UniqueManufacturerNameValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueManufacturerName {

    String message() default "{validation.manufacturer.name.unique}";

    String idField() default "id";

    String nameField() default "name";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

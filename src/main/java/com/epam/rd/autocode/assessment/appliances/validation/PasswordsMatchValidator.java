package com.epam.rd.autocode.assessment.appliances.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

import java.util.Objects;

public class PasswordsMatchValidator implements ConstraintValidator<PasswordsMatch, Object> {

    private String passwordField;
    private String confirmField;

    @Override
    public void initialize(PasswordsMatch annotation) {
        this.passwordField = annotation.passwordField();
        this.confirmField = annotation.confirmField();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(value);
        Object password = wrapper.getPropertyValue(passwordField);
        Object confirm = wrapper.getPropertyValue(confirmField);
        if (Objects.equals(password, confirm)) {
            return true;
        }
        // attach the error to the confirmation field so the form highlights it
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(confirmField)
                .addConstraintViolation();
        return false;
    }
}

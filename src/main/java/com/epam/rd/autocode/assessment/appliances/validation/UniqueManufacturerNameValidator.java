package com.epam.rd.autocode.assessment.appliances.validation;

import com.epam.rd.autocode.assessment.appliances.repository.ManufacturerRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

public class UniqueManufacturerNameValidator implements ConstraintValidator<UniqueManufacturerName, Object> {

    private final ManufacturerRepository manufacturerRepository;

    private String idField;
    private String nameField;

    public UniqueManufacturerNameValidator(ManufacturerRepository manufacturerRepository) {
        this.manufacturerRepository = manufacturerRepository;
    }

    @Override
    public void initialize(UniqueManufacturerName annotation) {
        this.idField = annotation.idField();
        this.nameField = annotation.nameField();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(value);
        Object nameValue = wrapper.getPropertyValue(nameField);
        if (!(nameValue instanceof String name) || name.isBlank()) {
            return true; // emptiness is checked by @NotBlank
        }
        Long id = (Long) wrapper.getPropertyValue(idField);
        name = name.trim();

        boolean taken = id == null
                ? manufacturerRepository.existsByNameIgnoreCase(name)
                : manufacturerRepository.existsByNameIgnoreCaseAndIdNot(name, id);

        if (!taken) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(nameField)
                .addConstraintViolation();
        return false;
    }
}

package com.epam.rd.autocode.assessment.appliances.validation;

import com.epam.rd.autocode.assessment.appliances.repository.ClientRepository;
import com.epam.rd.autocode.assessment.appliances.repository.EmployeeRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

public class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, Object> {

    private final ClientRepository clientRepository;
    private final EmployeeRepository employeeRepository;

    private UserType type;
    private String idField;
    private String emailField;

    public UniqueEmailValidator(ClientRepository clientRepository, EmployeeRepository employeeRepository) {
        this.clientRepository = clientRepository;
        this.employeeRepository = employeeRepository;
    }

    @Override
    public void initialize(UniqueEmail annotation) {
        this.type = annotation.type();
        this.idField = annotation.idField();
        this.emailField = annotation.emailField();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(value);
        Object emailValue = wrapper.getPropertyValue(emailField);
        if (!(emailValue instanceof String email) || email.isBlank()) {
            return true; // emptiness is checked by @NotBlank
        }
        Long id = (Long) wrapper.getPropertyValue(idField);
        email = email.trim();

        boolean taken = switch (type) {
            case CLIENT -> (id == null
                    ? clientRepository.existsByEmailIgnoreCase(email)
                    : clientRepository.existsByEmailIgnoreCaseAndIdNot(email, id))
                    || employeeRepository.existsByEmailIgnoreCase(email);
            case EMPLOYEE -> (id == null
                    ? employeeRepository.existsByEmailIgnoreCase(email)
                    : employeeRepository.existsByEmailIgnoreCaseAndIdNot(email, id))
                    || clientRepository.existsByEmailIgnoreCase(email);
        };

        if (!taken) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(emailField)
                .addConstraintViolation();
        return false;
    }
}

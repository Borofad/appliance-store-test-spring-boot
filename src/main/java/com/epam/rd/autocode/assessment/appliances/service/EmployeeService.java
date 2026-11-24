package com.epam.rd.autocode.assessment.appliances.service;

import com.epam.rd.autocode.assessment.appliances.dto.EmployeeDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EmployeeService {

    /** Paged, sorted search by name, email or department; empty query returns everything. */
    Page<EmployeeDto> search(String query, Pageable pageable);

    EmployeeDto findById(Long id);

    /** For the logged-in employee (the login name is the email). */
    EmployeeDto findByEmail(String email);

    /** Creates the employee; the password is stored as a BCrypt hash. */
    EmployeeDto create(EmployeeDto dto);

    /** Updates the employee; an empty password keeps the current one. */
    EmployeeDto update(Long id, EmployeeDto dto);

    /** @throws com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException if the employee approved orders */
    void delete(Long id);
}

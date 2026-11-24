package com.epam.rd.autocode.assessment.appliances.service.impl;

import com.epam.rd.autocode.assessment.appliances.dto.EmployeeDto;
import com.epam.rd.autocode.assessment.appliances.exception.EmailAlreadyExistsException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceNotFoundException;
import com.epam.rd.autocode.assessment.appliances.model.Employee;
import com.epam.rd.autocode.assessment.appliances.repository.ClientRepository;
import com.epam.rd.autocode.assessment.appliances.repository.EmployeeRepository;
import com.epam.rd.autocode.assessment.appliances.repository.OrdersRepository;
import com.epam.rd.autocode.assessment.appliances.service.EmployeeService;
import com.epam.rd.autocode.assessment.appliances.service.SearchUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final ClientRepository clientRepository;
    private final OrdersRepository ordersRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;

    @Override
    public Page<EmployeeDto> search(String query, Pageable pageable) {
        return employeeRepository.search(SearchUtils.normalize(query), pageable).map(this::toDto);
    }

    @Override
    public EmployeeDto findById(Long id) {
        return toDto(getEntity(id));
    }

    @Override
    public EmployeeDto findByEmail(String email) {
        return employeeRepository.findByEmail(email)
                .map(this::toDto)
                .orElseThrow(() -> ResourceNotFoundException.employee(email));
    }

    @Override
    @Transactional
    public EmployeeDto create(EmployeeDto dto) {
        String email = dto.getEmail().trim();
        assertEmailFree(email, null);
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required for a new employee");
        }
        Employee employee = new Employee();
        employee.setName(dto.getName().trim());
        employee.setEmail(email);
        employee.setDepartment(dto.getDepartment().trim());
        employee.setPassword(passwordEncoder.encode(dto.getPassword()));
        Employee saved = employeeRepository.save(employee);
        log.info("Employee created: id={}, email={}", saved.getId(), saved.getEmail());
        return toDto(saved);
    }

    @Override
    @Transactional
    public EmployeeDto update(Long id, EmployeeDto dto) {
        Employee employee = getEntity(id);
        String email = dto.getEmail().trim();
        assertEmailFree(email, id);
        employee.setName(dto.getName().trim());
        employee.setEmail(email);
        employee.setDepartment(dto.getDepartment().trim());
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            employee.setPassword(passwordEncoder.encode(dto.getPassword()));
            log.info("Password changed for employee id={}", id);
        }
        log.info("Employee updated: id={}", id);
        return toDto(employeeRepository.save(employee));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Employee employee = getEntity(id);
        if (ordersRepository.existsByEmployeeId(id)) {
            log.warn("Refused to delete employee id={}: approved orders exist", id);
            throw ResourceInUseException.employee();
        }
        employeeRepository.delete(employee);
        log.info("Employee deleted: id={}", id);
    }

    /** Emails are unique across clients AND employees, because login looks in both. */
    private void assertEmailFree(String email, Long ownId) {
        boolean takenByEmployee = ownId == null
                ? employeeRepository.existsByEmailIgnoreCase(email)
                : employeeRepository.existsByEmailIgnoreCaseAndIdNot(email, ownId);
        if (takenByEmployee || clientRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }
    }

    private Employee getEntity(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.employee(id));
    }

    private EmployeeDto toDto(Employee employee) {
        return modelMapper.map(employee, EmployeeDto.class);
    }
}

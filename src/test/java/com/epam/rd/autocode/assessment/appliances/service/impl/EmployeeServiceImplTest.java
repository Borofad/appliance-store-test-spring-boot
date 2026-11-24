package com.epam.rd.autocode.assessment.appliances.service.impl;

import com.epam.rd.autocode.assessment.appliances.config.MapperConfig;
import com.epam.rd.autocode.assessment.appliances.dto.EmployeeDto;
import com.epam.rd.autocode.assessment.appliances.exception.EmailAlreadyExistsException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceNotFoundException;
import com.epam.rd.autocode.assessment.appliances.model.Employee;
import com.epam.rd.autocode.assessment.appliances.repository.ClientRepository;
import com.epam.rd.autocode.assessment.appliances.repository.EmployeeRepository;
import com.epam.rd.autocode.assessment.appliances.repository.OrdersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private EmployeeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmployeeServiceImpl(employeeRepository, clientRepository, ordersRepository,
                passwordEncoder, new MapperConfig().modelMapper());
    }

    private Employee employee(Long id) {
        return new Employee(id, "Bob", "bob@x.com", "OLD_HASH", "sales");
    }

    private EmployeeDto dto(String password) {
        EmployeeDto d = new EmployeeDto();
        d.setName(" Bob ");
        d.setEmail(" bob@x.com ");
        d.setDepartment(" sales ");
        d.setPassword(password);
        d.setConfirmPassword(password);
        return d;
    }

    @Test
    void search_nullQuery_usesEmptyText() {
        Pageable pageable = PageRequest.of(0, 10);
        when(employeeRepository.search("", pageable)).thenReturn(new PageImpl<>(List.of(employee(1L))));

        Page<EmployeeDto> result = service.search(null, pageable);

        assertEquals(1, result.getContent().size());
        assertEquals("sales", result.getContent().get(0).getDepartment());
    }

    @Test
    void findById_neverExposesPasswordHash() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee(1L)));

        EmployeeDto result = service.findById(1L);

        assertEquals("Bob", result.getName());
        assertNull(result.getPassword());
    }

    @Test
    void findById_notFound_throws() {
        when(employeeRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findById(9L));
    }

    @Test
    void findByEmail_found() {
        when(employeeRepository.findByEmail("bob@x.com")).thenReturn(Optional.of(employee(1L)));

        assertEquals(1L, service.findByEmail("bob@x.com").getId());
    }

    @Test
    void findByEmail_notFound_throws() {
        when(employeeRepository.findByEmail("nobody@x.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findByEmail("nobody@x.com"));
    }

    @Test
    void create_encodesPasswordAndTrimsFields() {
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("HASH");
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            e.setId(3L);
            return e;
        });

        EmployeeDto result = service.create(dto("Passw0rd!"));

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());
        Employee saved = captor.getValue();
        assertEquals("HASH", saved.getPassword());
        assertEquals("Bob", saved.getName());
        assertEquals("bob@x.com", saved.getEmail());
        assertEquals("sales", saved.getDepartment());
        assertEquals(3L, result.getId());
        assertNull(result.getPassword());
    }

    @Test
    void create_emailUsedByEmployee_throws() {
        when(employeeRepository.existsByEmailIgnoreCase("bob@x.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> service.create(dto("Passw0rd!")));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void create_emailUsedByClient_throws() {
        when(clientRepository.existsByEmailIgnoreCase("bob@x.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> service.create(dto("Passw0rd!")));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void create_blankPassword_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.create(dto(null)));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void update_emptyPassword_keepsCurrentHash() {
        Employee existing = employee(1L);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(employeeRepository.save(existing)).thenReturn(existing);

        service.update(1L, dto(""));

        assertEquals("OLD_HASH", existing.getPassword());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void update_newPassword_isEncoded() {
        Employee existing = employee(1L);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("N3w!Password")).thenReturn("NEW_HASH");
        when(employeeRepository.save(existing)).thenReturn(existing);

        service.update(1L, dto("N3w!Password"));

        assertEquals("NEW_HASH", existing.getPassword());
    }

    @Test
    void update_changesFields() {
        Employee existing = employee(1L);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(employeeRepository.save(existing)).thenReturn(existing);
        EmployeeDto form = dto(null);
        form.setDepartment("security");

        EmployeeDto result = service.update(1L, form);

        assertEquals("security", existing.getDepartment());
        assertEquals("security", result.getDepartment());
    }

    @Test
    void update_emailTakenByOtherEmployee_throws() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee(1L)));
        when(employeeRepository.existsByEmailIgnoreCaseAndIdNot("bob@x.com", 1L)).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> service.update(1L, dto(null)));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void update_notFound_throws() {
        when(employeeRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update(9L, dto(null)));
    }

    @Test
    void delete_removesEmployee() {
        Employee existing = employee(1L);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(ordersRepository.existsByEmployeeId(1L)).thenReturn(false);

        service.delete(1L);

        verify(employeeRepository).delete(existing);
    }

    @Test
    void delete_employeeApprovedOrders_throws() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee(1L)));
        when(ordersRepository.existsByEmployeeId(1L)).thenReturn(true);

        assertThrows(ResourceInUseException.class, () -> service.delete(1L));
        verify(employeeRepository, never()).delete(any());
    }

    @Test
    void delete_notFound_throws() {
        when(employeeRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(9L));
    }
}

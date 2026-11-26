package com.epam.rd.autocode.assessment.appliances.security;

import com.epam.rd.autocode.assessment.appliances.model.Client;
import com.epam.rd.autocode.assessment.appliances.model.Employee;
import com.epam.rd.autocode.assessment.appliances.repository.ClientRepository;
import com.epam.rd.autocode.assessment.appliances.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private ClientRepository clientRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(clientRepository, employeeRepository);
    }

    private List<String> authorities(UserDetails details) {
        return details.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void client_isFoundWithClientRole() {
        when(clientRepository.findByEmailIgnoreCase("anna@x.com"))
                .thenReturn(Optional.of(new Client(1L, "Anna", "anna@x.com", "HASH", "5326-0001")));

        UserDetails details = service.loadUserByUsername("anna@x.com");

        assertEquals("anna@x.com", details.getUsername());
        assertEquals("HASH", details.getPassword());
        assertEquals(List.of("ROLE_CLIENT"), authorities(details));
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void employee_isFoundWhenThereIsNoClient() {
        when(clientRepository.findByEmailIgnoreCase("bob@x.com")).thenReturn(Optional.empty());
        when(employeeRepository.findByEmailIgnoreCase("bob@x.com"))
                .thenReturn(Optional.of(new Employee(3L, "Bob", "bob@x.com", "HASH2", "sales")));

        UserDetails details = service.loadUserByUsername("bob@x.com");

        assertEquals(List.of("ROLE_EMPLOYEE"), authorities(details));
        assertEquals("HASH2", details.getPassword());
    }

    @Test
    void details_carryIdAndName() {
        when(clientRepository.findByEmailIgnoreCase("anna@x.com"))
                .thenReturn(Optional.of(new Client(7L, "Anna", "anna@x.com", "HASH", "5326-0001")));

        AppUserDetails details = (AppUserDetails) service.loadUserByUsername("anna@x.com");

        assertEquals(7L, details.getId());
        assertEquals("Anna", details.getName());
    }

    @Test
    void usernameIsTheEmailAsStored_notAsTyped() {
        when(clientRepository.findByEmailIgnoreCase("MERCURY@GMAIL.COM"))
                .thenReturn(Optional.of(new Client(1L, "Mercury", "mercury@gmail.com", "HASH", "5326-1111")));

        UserDetails details = service.loadUserByUsername("MERCURY@GMAIL.COM");

        assertEquals("mercury@gmail.com", details.getUsername());
    }

    @Test
    void surroundingSpacesAreIgnored() {
        when(clientRepository.findByEmailIgnoreCase("anna@x.com"))
                .thenReturn(Optional.of(new Client(1L, "Anna", "anna@x.com", "HASH", "5326-0001")));

        assertEquals("anna@x.com", service.loadUserByUsername("  anna@x.com ").getUsername());
    }

    @Test
    void unknownUser_throws() {
        when(clientRepository.findByEmailIgnoreCase("nobody@x.com")).thenReturn(Optional.empty());
        when(employeeRepository.findByEmailIgnoreCase("nobody@x.com")).thenReturn(Optional.empty());

        UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("nobody@x.com"));

        assertFalse(ex.getMessage().contains("nobody@x.com"));
    }

    @Test
    void blankOrNullLogin_throwsWithoutTouchingTheDatabase() {
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("   "));
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername(null));

        verifyNoInteractions(clientRepository, employeeRepository);
    }
}

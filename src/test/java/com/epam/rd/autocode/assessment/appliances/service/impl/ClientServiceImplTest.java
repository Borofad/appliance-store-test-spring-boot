package com.epam.rd.autocode.assessment.appliances.service.impl;

import com.epam.rd.autocode.assessment.appliances.config.MapperConfig;
import com.epam.rd.autocode.assessment.appliances.dto.ClientDto;
import com.epam.rd.autocode.assessment.appliances.exception.EmailAlreadyExistsException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceNotFoundException;
import com.epam.rd.autocode.assessment.appliances.model.Client;
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
class ClientServiceImplTest {

    @Mock
    private ClientRepository clientRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private ClientServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClientServiceImpl(clientRepository, employeeRepository, ordersRepository,
                passwordEncoder, new MapperConfig().modelMapper());
    }

    private Client client(Long id) {
        return new Client(id, "Anna", "anna@x.com", "OLD_HASH", "5326-0001");
    }

    private ClientDto dto(String password) {
        ClientDto d = new ClientDto();
        d.setName("  Anna ");
        d.setEmail(" anna@x.com ");
        d.setCard(" 5326-0001 ");
        d.setPassword(password);
        d.setConfirmPassword(password);
        return d;
    }

    @Test
    void search_nullQuery_usesEmptyText() {
        Pageable pageable = PageRequest.of(0, 10);
        when(clientRepository.search("", pageable)).thenReturn(new PageImpl<>(List.of(client(1L))));

        Page<ClientDto> result = service.search(null, pageable);

        assertEquals(1, result.getContent().size());
        assertEquals("anna@x.com", result.getContent().get(0).getEmail());
    }

    @Test
    void findById_neverExposesPasswordHash() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client(1L)));

        ClientDto result = service.findById(1L);

        assertEquals("Anna", result.getName());
        assertNull(result.getPassword());
    }

    @Test
    void findById_notFound_throws() {
        when(clientRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findById(9L));
    }

    @Test
    void findByEmail_found() {
        when(clientRepository.findByEmail("anna@x.com")).thenReturn(Optional.of(client(1L)));

        assertEquals(1L, service.findByEmail("anna@x.com").getId());
    }

    @Test
    void findByEmail_notFound_throws() {
        when(clientRepository.findByEmail("nobody@x.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findByEmail("nobody@x.com"));
    }

    @Test
    void create_encodesPasswordAndTrimsFields() {
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("HASH");
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            c.setId(7L);
            return c;
        });

        ClientDto result = service.create(dto("Passw0rd!"));

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());
        Client saved = captor.getValue();
        assertEquals("HASH", saved.getPassword());
        assertEquals("Anna", saved.getName());
        assertEquals("anna@x.com", saved.getEmail());
        assertEquals("5326-0001", saved.getCard());
        assertEquals(7L, result.getId());
        assertNull(result.getPassword());
    }

    @Test
    void create_emailUsedByClient_throws() {
        when(clientRepository.existsByEmailIgnoreCase("anna@x.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> service.create(dto("Passw0rd!")));
        verify(clientRepository, never()).save(any());
    }

    @Test
    void create_emailUsedByEmployee_throws() {
        when(employeeRepository.existsByEmailIgnoreCase("anna@x.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> service.create(dto("Passw0rd!")));
        verify(clientRepository, never()).save(any());
    }

    @Test
    void create_blankPassword_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.create(dto("  ")));
        verify(clientRepository, never()).save(any());
    }

    @Test
    void update_emptyPassword_keepsCurrentHash() {
        Client existing = client(1L);
        when(clientRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(clientRepository.save(existing)).thenReturn(existing);

        service.update(1L, dto(""));

        assertEquals("OLD_HASH", existing.getPassword());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void update_newPassword_isEncoded() {
        Client existing = client(1L);
        when(clientRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("N3w!Password")).thenReturn("NEW_HASH");
        when(clientRepository.save(existing)).thenReturn(existing);

        service.update(1L, dto("N3w!Password"));

        assertEquals("NEW_HASH", existing.getPassword());
    }

    @Test
    void update_changesFields() {
        Client existing = client(1L);
        when(clientRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(clientRepository.save(existing)).thenReturn(existing);
        ClientDto form = dto(null);
        form.setName("Anna Maria");
        form.setCard("9999");

        ClientDto result = service.update(1L, form);

        assertEquals("Anna Maria", existing.getName());
        assertEquals("9999", existing.getCard());
        assertEquals("Anna Maria", result.getName());
    }

    @Test
    void update_emailTakenByOtherClient_throws() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client(1L)));
        when(clientRepository.existsByEmailIgnoreCaseAndIdNot("anna@x.com", 1L)).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> service.update(1L, dto(null)));
        verify(clientRepository, never()).save(any());
    }

    @Test
    void update_notFound_throws() {
        when(clientRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update(9L, dto(null)));
    }

    @Test
    void delete_removesClient() {
        Client existing = client(1L);
        when(clientRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(ordersRepository.existsByClientId(1L)).thenReturn(false);

        service.delete(1L);

        verify(clientRepository).delete(existing);
    }

    @Test
    void delete_clientHasOrders_throws() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client(1L)));
        when(ordersRepository.existsByClientId(1L)).thenReturn(true);

        assertThrows(ResourceInUseException.class, () -> service.delete(1L));
        verify(clientRepository, never()).delete(any());
    }

    @Test
    void delete_notFound_throws() {
        when(clientRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(9L));
    }
}

package com.epam.rd.autocode.assessment.appliances.service;

import com.epam.rd.autocode.assessment.appliances.dto.ClientDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ClientService {

    /** Paged, sorted search by name or email; empty query returns everything. */
    Page<ClientDto> search(String query, Pageable pageable);

    /** All clients sorted by name (for dropdowns). */
    List<ClientDto> findAll();

    ClientDto findById(Long id);

    /** For the logged-in client (the login name is the email). */
    ClientDto findByEmail(String email);

    /** Creates the client; the password is stored as a BCrypt hash. */
    ClientDto create(ClientDto dto);

    /** Updates the client; an empty password keeps the current one. */
    ClientDto update(Long id, ClientDto dto);

    /** @throws com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException if the client has orders */
    void delete(Long id);
}
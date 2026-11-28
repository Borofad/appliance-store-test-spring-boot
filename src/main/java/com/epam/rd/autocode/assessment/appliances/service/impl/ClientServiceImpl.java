package com.epam.rd.autocode.assessment.appliances.service.impl;

import com.epam.rd.autocode.assessment.appliances.dto.ClientDto;
import com.epam.rd.autocode.assessment.appliances.exception.EmailAlreadyExistsException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceNotFoundException;
import com.epam.rd.autocode.assessment.appliances.model.Client;
import com.epam.rd.autocode.assessment.appliances.repository.ClientRepository;
import com.epam.rd.autocode.assessment.appliances.repository.EmployeeRepository;
import com.epam.rd.autocode.assessment.appliances.repository.OrdersRepository;
import com.epam.rd.autocode.assessment.appliances.service.ClientService;
import com.epam.rd.autocode.assessment.appliances.service.SearchUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final EmployeeRepository employeeRepository;
    private final OrdersRepository ordersRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;

    @Override
    public Page<ClientDto> search(String query, Pageable pageable) {
        return clientRepository.search(SearchUtils.normalize(query), pageable).map(this::toDto);
    }

    @Override
    public List<ClientDto> findAll() {
        return clientRepository.findAll(Sort.by("name")).stream().map(this::toDto).toList();
    }

    @Override
    public ClientDto findById(Long id) {
        return toDto(getEntity(id));
    }

    @Override
    public ClientDto findByEmail(String email) {
        return clientRepository.findByEmail(email)
                .map(this::toDto)
                .orElseThrow(() -> ResourceNotFoundException.client(email));
    }

    @Override
    @Transactional
    public ClientDto create(ClientDto dto) {
        String email = dto.getEmail().trim();
        assertEmailFree(email, null);
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required for a new client");
        }
        Client client = new Client();
        client.setName(dto.getName().trim());
        client.setEmail(email);
        client.setCard(dto.getCard().trim());
        client.setPassword(passwordEncoder.encode(dto.getPassword()));
        Client saved = clientRepository.save(client);
        log.info("Client created: id={}, email={}", saved.getId(), saved.getEmail());
        return toDto(saved);
    }

    @Override
    @Transactional
    public ClientDto update(Long id, ClientDto dto) {
        Client client = getEntity(id);
        String email = dto.getEmail().trim();
        assertEmailFree(email, id);
        client.setName(dto.getName().trim());
        client.setEmail(email);
        client.setCard(dto.getCard().trim());
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            client.setPassword(passwordEncoder.encode(dto.getPassword()));
            log.info("Password changed for client id={}", id);
        }
        log.info("Client updated: id={}", id);
        return toDto(clientRepository.save(client));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Client client = getEntity(id);
        if (ordersRepository.existsByClientId(id)) {
            log.warn("Refused to delete client id={}: has orders", id);
            throw ResourceInUseException.client();
        }
        clientRepository.delete(client);
        log.info("Client deleted: id={}", id);
    }

    /** Emails are unique across clients AND employees, because login looks in both. */
    private void assertEmailFree(String email, Long ownId) {
        boolean takenByClient = ownId == null
                ? clientRepository.existsByEmailIgnoreCase(email)
                : clientRepository.existsByEmailIgnoreCaseAndIdNot(email, ownId);
        if (takenByClient || employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }
    }

    private Client getEntity(Long id) {
        return clientRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.client(id));
    }

    private ClientDto toDto(Client client) {
        return modelMapper.map(client, ClientDto.class);
    }
}
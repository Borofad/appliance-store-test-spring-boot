package com.epam.rd.autocode.assessment.appliances.security;

import com.epam.rd.autocode.assessment.appliances.model.User;
import com.epam.rd.autocode.assessment.appliances.repository.ClientRepository;
import com.epam.rd.autocode.assessment.appliances.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Database-backed login: finds the user by email, first among the clients, then among the employees.
 * (Emails are unique across both tables, so at most one of them matches.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final ClientRepository clientRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Login name is empty");
        }
        String email = username.trim();

        return clientRepository.findByEmailIgnoreCase(email).<User>map(client -> client)
                .or(() -> employeeRepository.findByEmailIgnoreCase(email).map(employee -> employee))
                .map(this::toUserDetails)
                .orElseThrow(() -> {
                    // no login name in the message or log: it comes from the user
                    log.debug("Login lookup found no user");
                    return new UsernameNotFoundException("User not found");
                });
    }

    private UserDetails toUserDetails(User user) {
        return new AppUserDetails(user.getId(), user.getName(), user.getEmail(), user.getPassword(), user.getRole());
    }
}

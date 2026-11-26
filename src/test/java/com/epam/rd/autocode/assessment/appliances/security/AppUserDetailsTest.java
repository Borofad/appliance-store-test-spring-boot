package com.epam.rd.autocode.assessment.appliances.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppUserDetailsTest {

    private final AppUserDetails details = new AppUserDetails(1L, "Anna", "anna@x.com", "HASH", "CLIENT");

    @Test
    void usernameIsTheEmail() {
        assertEquals("anna@x.com", details.getUsername());
    }

    @Test
    void roleBecomesRolePrefixedAuthority() {
        List<String> authorities = details.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();

        assertEquals(List.of("ROLE_CLIENT"), authorities);
    }

    @Test
    void exposesIdNameAndPasswordHash() {
        assertEquals(1L, details.getId());
        assertEquals("Anna", details.getName());
        assertEquals("HASH", details.getPassword());
    }

    @Test
    void accountIsAlwaysActive() {
        assertTrue(details.isEnabled());
        assertTrue(details.isAccountNonExpired());
        assertTrue(details.isAccountNonLocked());
        assertTrue(details.isCredentialsNonExpired());
    }

    @Test
    void toStringNeverContainsThePassword() {
        assertFalse(details.toString().contains("HASH"));
    }
}

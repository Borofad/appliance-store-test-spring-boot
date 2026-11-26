package com.epam.rd.autocode.assessment.appliances.aspect;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Logs security events: successful and failed logins.
 * Spring Security publishes these events by itself once form login is configured.
 * Passwords are never logged. Access-denied events are logged by GlobalExceptionHandler.
 */
@Slf4j
@Component
public class SecurityEventsLogger {

    private static final int MAX_NAME_LENGTH = 100;

    @EventListener
    public void onLoginSuccess(AuthenticationSuccessEvent event) {
        List<String> roles = event.getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        log.info("SECURITY: login succeeded for '{}' with roles {}",
                sanitize(event.getAuthentication().getName()), roles);
    }

    @EventListener
    public void onLoginFailure(AbstractAuthenticationFailureEvent event) {
        log.warn("SECURITY: login failed for '{}': {}",
                sanitize(event.getAuthentication().getName()), event.getException().getClass().getSimpleName());
    }

    /** The login name comes from the user: strip line breaks (log forging) and cap the length. */
    private String sanitize(String value) {
        String clean = String.valueOf(value).replaceAll("[\\r\\n\\t]+", "_");
        return clean.length() > MAX_NAME_LENGTH ? clean.substring(0, MAX_NAME_LENGTH) + "..." : clean;
    }
}
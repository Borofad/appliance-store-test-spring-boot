package com.epam.rd.autocode.assessment.appliances.aspect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityEventsLoggerTest {

    private final SecurityEventsLogger listener = new SecurityEventsLogger();

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(SecurityEventsLogger.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void loginSuccess_logsUserAndRolesAtInfo() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "bob@x.com", null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));

        listener.onLoginSuccess(new AuthenticationSuccessEvent(authentication));

        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("bob@x.com"));
        assertTrue(event.getFormattedMessage().contains("ROLE_EMPLOYEE"));
    }

    @Test
    void loginFailure_logsWarningWithoutThePassword() {
        var authentication = new UsernamePasswordAuthenticationToken("bob@x.com", "wrong-password");

        listener.onLoginFailure(new AuthenticationFailureBadCredentialsEvent(
                authentication, new BadCredentialsException("Bad credentials")));

        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("bob@x.com"));
        assertTrue(event.getFormattedMessage().contains("BadCredentialsException"));
        assertFalse(event.getFormattedMessage().contains("wrong-password"));
    }

    @Test
    void loginName_withLineBreaks_cannotForgeLogLines() {
        var authentication = new UsernamePasswordAuthenticationToken("evil\nSECURITY: fake entry", "x");

        listener.onLoginFailure(new AuthenticationFailureBadCredentialsEvent(
                authentication, new BadCredentialsException("Bad credentials")));

        assertFalse(appender.list.get(0).getFormattedMessage().contains("\n"));
    }
}
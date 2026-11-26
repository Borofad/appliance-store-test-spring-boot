package com.epam.rd.autocode.assessment.appliances.controller;

import com.epam.rd.autocode.assessment.appliances.security.AppUserDetails;
import com.epam.rd.autocode.assessment.appliances.security.JwtCookieService;
import com.epam.rd.autocode.assessment.appliances.security.JwtProperties;
import com.epam.rd.autocode.assessment.appliances.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthenticationManager authenticationManager;

    private JwtService jwtService;
    private AuthController controller;
    private MockHttpServletResponse response;
    private AppUserDetails mercury;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-0123456789-0123456789-0123456789");
        properties.setExpiration(Duration.ofMinutes(30));
        jwtService = new JwtService(properties);
        controller = new AuthController(authenticationManager, jwtService, new JwtCookieService(properties, jwtService));

        mercury = new AppUserDetails(1L, "Mercury", "mercury@gmail.com", "HASH", "CLIENT");
        response = new MockHttpServletResponse();
    }

    @Test
    void login_success_setsAJwtCookieForTheUserAndRedirectsHome() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(mercury, null, mercury.getAuthorities()));

        String view = controller.login("  mercury@gmail.com ", "111", response);

        assertEquals("redirect:/", view);
        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(header);
        assertTrue(header.startsWith("APPLIANCES_JWT="));
        assertTrue(header.contains("HttpOnly"));
        String token = header.substring(header.indexOf('=') + 1, header.indexOf(';'));
        assertEquals(Optional.of("mercury@gmail.com"), jwtService.extractUsername(token));
    }

    @Test
    void login_passesTheTrimmedEmailAndThePasswordToTheAuthenticationManager() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(mercury, null, mercury.getAuthorities()));

        controller.login("  mercury@gmail.com ", "111", response);

        ArgumentCaptor<Authentication> captor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertEquals("mercury@gmail.com", captor.getValue().getName());
        assertEquals("111", captor.getValue().getCredentials());
        assertFalse(captor.getValue().isAuthenticated());
    }

    @Test
    void login_badCredentials_redirectsWithErrorAndSetsNoCookie() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        String view = controller.login("mercury@gmail.com", "wrong", response);

        assertEquals("redirect:/login?error", view);
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void login_veryLongPassword_isRejectedWithoutCheckingIt() {
        String view = controller.login("mercury@gmail.com", "x".repeat(AuthController.MAX_PASSWORD_LENGTH + 1), response);

        assertEquals("redirect:/login?error", view);
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void loginPage_isShownToAnonymousVisitors() {
        assertEquals("login", controller.loginPage(null));
    }

    @Test
    void loginPage_sendsLoggedInUsersHome() {
        assertEquals("redirect:/", controller.loginPage(mercury));
    }
}

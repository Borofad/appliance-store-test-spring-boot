package com.epam.rd.autocode.assessment.appliances.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private UserDetailsService userDetailsService;

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;
    private AppUserDetails bob;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-0123456789-0123456789-0123456789");
        properties.setExpiration(Duration.ofMinutes(30));
        jwtService = new JwtService(properties);
        filter = new JwtAuthenticationFilter(jwtService, new JwtCookieService(properties, jwtService), userDetailsService);

        bob = new AppUserDetails(3L, "Bob", "bob@x.com", "HASH", "EMPLOYEE");
        request = new MockHttpServletRequest("GET", "/orders");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String validToken() {
        return jwtService.generateToken(bob);
    }

    private void assertChainContinued() {
        assertNotNull(chain.getRequest(), "the request must always continue down the filter chain");
    }

    private void assertAuthenticatedAsBob() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertSame(bob, authentication.getPrincipal());
        assertEquals(List.of("ROLE_EMPLOYEE"),
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    @Test
    void validCookie_authenticatesTheUser() throws Exception {
        when(userDetailsService.loadUserByUsername("bob@x.com")).thenReturn(bob);
        request.setCookies(new Cookie("APPLIANCES_JWT", validToken()));

        filter.doFilter(request, response, chain);

        assertAuthenticatedAsBob();
        assertChainContinued();
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void validBearerHeader_authenticatesTheUser() throws Exception {
        when(userDetailsService.loadUserByUsername("bob@x.com")).thenReturn(bob);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + validToken());

        filter.doFilter(request, response, chain);

        assertAuthenticatedAsBob();
        assertChainContinued();
    }

    @Test
    void bearerHeader_winsOverABadCookieAndLeavesTheCookieAlone() throws Exception {
        when(userDetailsService.loadUserByUsername("bob@x.com")).thenReturn(bob);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + validToken());
        request.setCookies(new Cookie("APPLIANCES_JWT", "garbage"));

        filter.doFilter(request, response, chain);

        assertAuthenticatedAsBob();
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void noToken_staysAnonymousAndContinues() throws Exception {
        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertChainContinued();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void garbageCookie_staysAnonymousAndIsRemovedFromTheBrowser() throws Exception {
        request.setCookies(new Cookie("APPLIANCES_JWT", "not-a-jwt"));

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertChainContinued();
        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void garbageBearerHeader_staysAnonymousWithoutTouchingCookies() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt");

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertChainContinued();
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void validTokenOfADeletedUser_staysAnonymousAndCookieIsRemoved() throws Exception {
        when(userDetailsService.loadUserByUsername("bob@x.com")).thenThrow(new UsernameNotFoundException("gone"));
        request.setCookies(new Cookie("APPLIANCES_JWT", validToken()));

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertChainContinued();
        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
    }

    @Test
    void otherAuthorizationSchemes_areIgnored() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic Ym9iOnB3");

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertChainContinued();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void existingAuthentication_isNotReplaced() throws Exception {
        var existing = UsernamePasswordAuthenticationToken.authenticated("someone-else", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        request.setCookies(new Cookie("APPLIANCES_JWT", validToken()));

        filter.doFilter(request, response, chain);

        assertSame(existing, SecurityContextHolder.getContext().getAuthentication());
        assertChainContinued();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void staticResources_areSkippedEvenWithAValidCookie() throws Exception {
        request = new MockHttpServletRequest("GET", "/css/main.css");
        request.setCookies(new Cookie("APPLIANCES_JWT", validToken()));

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertChainContinued();
        verifyNoInteractions(userDetailsService);
    }
}

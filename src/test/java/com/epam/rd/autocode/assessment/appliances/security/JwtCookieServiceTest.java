package com.epam.rd.autocode.assessment.appliances.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JwtCookieServiceTest {

    private JwtProperties properties;
    private JwtCookieService cookies;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret("unit-test-secret-0123456789-0123456789-0123456789");
        properties.setExpiration(Duration.ofMinutes(30));
        properties.setCookieSecure(true);
        cookies = new JwtCookieService(properties, new JwtService(properties));
        response = new MockHttpServletResponse();
    }

    @Test
    void addTokenCookie_setsSafeFlagsAndLifetime() {
        cookies.addTokenCookie(response, "abc.def.ghi");

        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(header);
        assertTrue(header.startsWith("APPLIANCES_JWT=abc.def.ghi"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("Secure"));
        assertTrue(header.contains("SameSite=Lax"));
        assertTrue(header.contains("Path=/"));
        assertTrue(header.contains("Max-Age=1800"));
    }

    @Test
    void addTokenCookie_isNotSecureWhenDisabled() {
        properties.setCookieSecure(false);

        cookies.addTokenCookie(response, "abc.def.ghi");

        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertFalse(header.contains("Secure"));
        assertTrue(header.contains("HttpOnly"));
    }

    @Test
    void addTokenCookie_usesTheConfiguredName() {
        properties.setCookieName("MY_COOKIE");

        cookies.addTokenCookie(response, "tok");

        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).startsWith("MY_COOKIE=tok"));
    }

    @Test
    void clearTokenCookie_expiresItImmediately() {
        cookies.clearTokenCookie(response);

        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(header.startsWith("APPLIANCES_JWT=;"));
        assertTrue(header.contains("Max-Age=0"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("Path=/"));
    }

    @Test
    void readToken_returnsTheCookieValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("APPLIANCES_JWT", "my.token.value"));

        assertEquals(Optional.of("my.token.value"), cookies.readToken(request));
    }

    @Test
    void readToken_emptyWhenThereIsNoCookie() {
        assertTrue(cookies.readToken(new MockHttpServletRequest()).isEmpty());
    }

    @Test
    void readToken_ignoresOtherCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OTHER", "value"));

        assertTrue(cookies.readToken(request).isEmpty());
    }

    @Test
    void readToken_emptyWhenTheValueIsBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("APPLIANCES_JWT", "  "));

        assertTrue(cookies.readToken(request).isEmpty());
    }
}

package com.epam.rd.autocode.assessment.appliances.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.util.Optional;

/**
 * Puts the JWT into a browser cookie, reads it back, and removes it on logout.
 *
 * The cookie is HttpOnly (JavaScript cannot read it, which protects the token from XSS),
 * SameSite=Lax (not sent on cross-site POSTs, an extra layer on top of CSRF tokens),
 * and Secure when app.jwt.cookie-secure is true. Its lifetime equals the token's.
 */
@Component
public class JwtCookieService {

    private final JwtProperties properties;
    private final JwtService jwtService;

    public JwtCookieService(JwtProperties properties, JwtService jwtService) {
        this.properties = properties;
        this.jwtService = jwtService;
    }

    /** Adds the login cookie to the response. */
    public void addTokenCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, jwtService.getExpiration()).toString());
    }

    /** Tells the browser to delete the login cookie (logout, or when the token is no longer valid). */
    public void clearTokenCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    /** The token from the request's cookie, if there is one. */
    public Optional<String> readToken(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, properties.getCookieName());
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(cookie.getValue());
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(properties.getCookieName(), value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}

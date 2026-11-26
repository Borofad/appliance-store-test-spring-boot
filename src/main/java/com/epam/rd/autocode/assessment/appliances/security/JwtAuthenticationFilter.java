package com.epam.rd.autocode.assessment.appliances.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Stateless login: on every request it looks for a JWT, and if the token is valid it authenticates
 * the user for that single request. There is no server-side session.
 *
 * The token is taken from the "Authorization: Bearer ..." header (API clients such as Postman) or,
 * if there is no such header, from the login cookie (browser). The user is loaded from the database
 * each time, so a deleted user loses access immediately and role changes apply at once.
 *
 * An invalid token is never an error here: the request just stays anonymous, and the security rules
 * decide what an anonymous visitor may see. A bad token in the cookie is removed from the browser.
 *
 * This class is deliberately NOT a @Component: Spring Boot would then also register it as a plain
 * servlet filter outside the security chain. SecurityConfig creates it and adds it to the chain.
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    /** Static files need no authentication, so skip the token check (and its database lookup). */
    private static final List<String> SKIPPED_PREFIXES = List.of("/css/", "/js/", "/images/", "/webjars/", "/favicon.ico");

    private final JwtService jwtService;
    private final JwtCookieService cookieService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   JwtCookieService cookieService,
                                   UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.cookieService = cookieService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return SKIPPED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<String> headerToken = bearerToken(request);
            boolean fromCookie = headerToken.isEmpty();
            Optional<String> token = fromCookie ? cookieService.readToken(request) : headerToken;
            token.ifPresent(value -> authenticate(value, fromCookie, request, response));
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token, boolean fromCookie,
                              HttpServletRequest request, HttpServletResponse response) {
        Optional<String> username = jwtService.extractUsername(token);
        if (username.isEmpty()) {
            reject("invalid or expired token", fromCookie, response);
            return;
        }
        try {
            UserDetails user = userDetailsService.loadUserByUsername(username.get());
            if (!jwtService.isValid(token, user)) {
                reject("token does not match the user", fromCookie, response);
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            log.debug("Request authenticated by JWT with authorities {}", user.getAuthorities());
        } catch (UsernameNotFoundException ex) {
            reject("user no longer exists", fromCookie, response);
        }
    }

    private void reject(String reason, boolean fromCookie, HttpServletResponse response) {
        log.debug("JWT ignored: {}", reason);
        if (fromCookie) {
            cookieService.clearTokenCookie(response);
        }
    }

    private Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}

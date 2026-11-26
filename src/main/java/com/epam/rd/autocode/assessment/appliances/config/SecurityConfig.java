package com.epam.rd.autocode.assessment.appliances.config;

import com.epam.rd.autocode.assessment.appliances.security.JwtAuthenticationFilter;
import com.epam.rd.autocode.assessment.appliances.security.JwtCookieService;
import com.epam.rd.autocode.assessment.appliances.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

import java.io.IOException;

/**
 * Stateless, JWT-based security.
 *
 * - No server session: the login token travels in an HttpOnly cookie (or a Bearer header).
 * - CSRF protection stays ON. Because there is no session, the CSRF token is kept in a cookie.
 * - Login and logout are handled by AuthController and the logout handler below.
 * - Public: the login page, error pages and static files. Everything else needs a login.
 *   (Role-based URL rules are added in a later step.)
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/login", "/error", "/error/**",
            "/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico"
    };

    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;
    private final UserDetailsService userDetailsService;

    /**
     * Spring builds the authentication manager from the single UserDetailsService bean
     * (CustomUserDetailsService) and the single PasswordEncoder bean (BCrypt).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // the default request cache would store the requested URL in a session
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .csrf(csrf -> csrf.csrfTokenRepository(new CookieCsrfTokenRepository()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(this::redirectToLoginOrReject))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .addLogoutHandler((request, response, authentication) -> {
                            jwtCookieService.clearTokenCookie(response);
                            log.info("SECURITY: logout for '{}'",
                                    authentication == null ? "unknown" : authentication.getName());
                        })
                        .logoutSuccessUrl("/login?logout"))
                // before LogoutFilter, so a logout request already knows who is logging out
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, jwtCookieService, userDetailsService),
                        LogoutFilter.class);
        return http.build();
    }

    /** Browser without a valid login: go to the login page. API client with a bearer token: plain 401. */
    private void redirectToLoginOrReject(HttpServletRequest request,
                                         HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            response.sendRedirect(request.getContextPath() + "/login");
        }
    }
}

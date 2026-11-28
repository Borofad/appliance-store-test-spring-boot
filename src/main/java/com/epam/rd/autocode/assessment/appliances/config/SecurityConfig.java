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
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

import java.io.IOException;

/**
 * Stateless, JWT-based security with role-based URL rules.
 *
 * - No server session: the login token travels in an HttpOnly cookie (or a Bearer header).
 * - CSRF protection stays ON. Because there is no session, the CSRF token is kept in a cookie.
 * - Login and logout are handled by AuthController and the logout handler below.
 *
 * Who may open what (a rule that does not match goes on to the next one, first match wins):
 *
 *   public ........ /login, error pages, static files
 *   both roles .... GET /appliances (to look at them), /orders/** (a client only ever sees their OWN orders,
 *                   which OrderController enforces), and the home page
 *   employees only  everything else under /appliances/** (add, edit, delete), /manufacturers/**,
 *                   /clients/**, /employees/**, and approving an order (POST /orders/{id}/approve)
 *
 * A logged-in user without the right role gets the 403 page; an anonymous visitor is sent to the login page.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    static final String CLIENT = "CLIENT";
    static final String EMPLOYEE = "EMPLOYEE";

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
                        // appliances: every logged-in user may look at the list, only employees may change anything
                        .requestMatchers(HttpMethod.GET, "/appliances").hasAnyRole(CLIENT, EMPLOYEE)
                        .requestMatchers("/appliances/**").hasRole(EMPLOYEE)
                        // management pages: employees only
                        .requestMatchers("/manufacturers/**", "/clients/**", "/employees/**").hasRole(EMPLOYEE)
                        // orders: both roles, but only employees may approve
                        .requestMatchers(HttpMethod.POST, "/orders/*/approve").hasRole(EMPLOYEE)
                        .requestMatchers("/orders/**").hasAnyRole(CLIENT, EMPLOYEE)
                        // everything else (the home page, ...): any logged-in user
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(this::redirectToLoginOrReject)
                        .accessDeniedHandler(this::forbidden))
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

    /**
     * A logged-in user tried something their role does not allow (or sent a bad CSRF token).
     * It is logged as a security event, then the 403 error page is shown.
     */
    private void forbidden(HttpServletRequest request,
                           HttpServletResponse response,
                           AccessDeniedException exception) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.warn("SECURITY: access denied for '{}' at {} {} ({})",
                authentication == null ? "unknown" : authentication.getName(),
                request.getMethod(), request.getRequestURI(), exception.getClass().getSimpleName());
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}

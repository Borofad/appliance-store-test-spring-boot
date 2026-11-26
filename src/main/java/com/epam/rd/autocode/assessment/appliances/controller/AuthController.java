package com.epam.rd.autocode.assessment.appliances.controller;

import com.epam.rd.autocode.assessment.appliances.security.AppUserDetails;
import com.epam.rd.autocode.assessment.appliances.security.JwtCookieService;
import com.epam.rd.autocode.assessment.appliances.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Login page and login action. Logout (POST /logout) is handled by Spring Security, see SecurityConfig.
 *
 * Login checks the email and password with the AuthenticationManager (BCrypt), then puts a JWT into an
 * HttpOnly cookie. Every failure gives the same generic message, so nobody can learn which emails exist.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    /** BCrypt only uses the first 72 bytes; anything this long is not a real password. */
    static final int MAX_PASSWORD_LENGTH = 128;

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtCookieService jwtCookieService;

    @GetMapping("/login")
    public String loginPage(@AuthenticationPrincipal AppUserDetails user) {
        return user != null ? "redirect:/" : "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam(name = "email", defaultValue = "") String email,
                        @RequestParam(name = "password", defaultValue = "") String password,
                        HttpServletResponse response) {
        if (password.length() > MAX_PASSWORD_LENGTH) {
            log.warn("SECURITY: login rejected, password too long");
            return "redirect:/login?error";
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email.trim(), password));
            UserDetails user = (UserDetails) authentication.getPrincipal();
            jwtCookieService.addTokenCookie(response, jwtService.generateToken(user));
            return "redirect:/";
        } catch (AuthenticationException ex) {
            // the failure itself is logged by SecurityEventsLogger
            return "redirect:/login?error";
        }
    }
}

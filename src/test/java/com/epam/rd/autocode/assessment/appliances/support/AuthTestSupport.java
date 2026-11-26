package com.epam.rd.autocode.assessment.appliances.support;

import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Helpers for MockMvc tests that go through the real security setup.
 *
 * CSRF tokens are obtained like a browser does it (the login page sets the XSRF-TOKEN cookie and holds the
 * matching hidden field). The spring-security-test helper csrf() is deliberately not used: it replaces the
 * application's cookie-based CSRF repository with a session-based one for the rest of the Spring context.
 */
public final class AuthTestSupport {

    public static final String JWT_COOKIE = "APPLIANCES_JWT";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final Pattern CSRF_FIELD = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    private AuthTestSupport() {
    }

    /** What a browser holds after opening a page with a form. */
    public record Csrf(Cookie cookie, String token) {
    }

    public static Csrf fetchCsrf(MockMvc mockMvc) throws Exception {
        MvcResult result = mockMvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie(CSRF_COOKIE);
        assertNotNull(cookie, "rendering a form must set the CSRF cookie");
        Matcher matcher = CSRF_FIELD.matcher(result.getResponse().getContentAsString());
        assertTrue(matcher.find(), "the login form must contain the hidden _csrf field");
        return new Csrf(cookie, matcher.group(1));
    }

    /** Logs in through the real login endpoint and returns the JWT cookie. */
    public static Cookie login(MockMvc mockMvc, String email, String password) throws Exception {
        Csrf csrf = fetchCsrf(mockMvc);
        MvcResult result = mockMvc.perform(post("/login")
                        .cookie(csrf.cookie())
                        .param("_csrf", csrf.token())
                        .param("email", email)
                        .param("password", password))
                .andExpect(redirectedUrl("/"))
                .andReturn();
        Cookie jwt = result.getResponse().getCookie(JWT_COOKIE);
        assertNotNull(jwt, "login must set the JWT cookie");
        return jwt;
    }

    public static Cookie loginAsEmployee(MockMvc mockMvc) throws Exception {
        return login(mockMvc, "phobos@gmail.com", "111");
    }

    public static Cookie loginAsClient(MockMvc mockMvc) throws Exception {
        return login(mockMvc, "mercury@gmail.com", "111");
    }

    /** A POST request that carries the login cookie, the CSRF cookie and the CSRF form field. */
    public static MockHttpServletRequestBuilder authenticatedPost(MockMvc mockMvc, Cookie jwt, String url)
            throws Exception {
        Csrf csrf = fetchCsrf(mockMvc);
        return post(url).cookie(jwt, csrf.cookie()).param("_csrf", csrf.token());
    }
}

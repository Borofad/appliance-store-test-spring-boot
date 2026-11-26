package com.epam.rd.autocode.assessment.appliances.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end check of the security setup against the real application (H2 and the seed users).
 *
 * CSRF tokens are obtained the way a browser does it: the login page sets the XSRF-TOKEN cookie and
 * contains the matching hidden form field. The spring-security-test helper csrf() is NOT used on purpose:
 * it replaces the application's cookie-based CSRF repository with a session-based one for all later
 * requests in the same Spring context, which would hide the real behaviour (and create sessions).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    private static final String JWT_COOKIE = "APPLIANCES_JWT";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final Pattern CSRF_FIELD = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    /** What a browser holds after opening a page with a form. */
    private record Csrf(Cookie cookie, String token) {
    }

    private Csrf fetchCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie(CSRF_COOKIE);
        assertNotNull(cookie, "rendering a form must set the CSRF cookie");
        Matcher matcher = CSRF_FIELD.matcher(result.getResponse().getContentAsString());
        assertTrue(matcher.find(), "the login form must contain the hidden _csrf field");
        return new Csrf(cookie, matcher.group(1));
    }

    private MockHttpServletRequestBuilder loginRequest(Csrf csrf, String email, String password) {
        return post("/login")
                .cookie(csrf.cookie())
                .param("_csrf", csrf.token())
                .param("email", email)
                .param("password", password);
    }

    /** Logs in through the real login endpoint and returns the JWT cookie. */
    private Cookie loginAs(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(loginRequest(fetchCsrf(), email, password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();
        Cookie cookie = result.getResponse().getCookie(JWT_COOKIE);
        assertNotNull(cookie, "login must set the JWT cookie");
        return cookie;
    }

    // ---------- public and protected pages ----------

    @Test
    void loginPage_isPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void loginPage_isTranslated() throws Exception {
        // "\u0412\u0445\u0456\u0434" is the Ukrainian "Log in" (written as escapes so file encoding cannot matter)
        mockMvc.perform(get("/login").param("lang", "uk"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\u0412\u0445\u0456\u0434")));
    }

    @Test
    void loginPage_isEnglishByDefault() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(content().string(containsString("Log in")));
    }

    @Test
    void homePage_withoutLogin_redirectsToTheLoginPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void staticFiles_areNotBehindTheLogin() throws Exception {
        MvcResult result = mockMvc.perform(get("/css/main.css")).andReturn();

        assertNull(result.getResponse().getRedirectedUrl());
    }

    // ---------- login ----------

    @Test
    void login_asClient_setsAnHttpOnlyJwtCookie() throws Exception {
        mockMvc.perform(loginRequest(fetchCsrf(), "mercury@gmail.com", "111"))
                .andExpect(redirectedUrl("/"))
                .andExpect(cookie().exists(JWT_COOKIE))
                .andExpect(cookie().httpOnly(JWT_COOKIE, true));
    }

    @Test
    void login_asEmployee_works() throws Exception {
        loginAs("phobos@gmail.com", "111");
    }

    @Test
    void login_emailLetterCaseDoesNotMatter() throws Exception {
        loginAs("MERCURY@GMAIL.COM", "111");
    }

    @Test
    void login_wrongPassword_isRejected() throws Exception {
        mockMvc.perform(loginRequest(fetchCsrf(), "mercury@gmail.com", "wrong"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(cookie().doesNotExist(JWT_COOKIE));
    }

    @Test
    void login_unknownUser_looksTheSameAsAWrongPassword() throws Exception {
        mockMvc.perform(loginRequest(fetchCsrf(), "nobody@gmail.com", "111"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(cookie().doesNotExist(JWT_COOKIE));
    }

    @Test
    void login_veryLongPassword_isRejected() throws Exception {
        mockMvc.perform(loginRequest(fetchCsrf(), "mercury@gmail.com", "x".repeat(200)))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void login_withoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(post("/login")
                        .param("email", "mercury@gmail.com")
                        .param("password", "111"))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_withAWrongCsrfToken_isForbidden() throws Exception {
        Csrf csrf = fetchCsrf();

        mockMvc.perform(post("/login")
                        .cookie(csrf.cookie())
                        .param("_csrf", "bogus")
                        .param("email", "mercury@gmail.com")
                        .param("password", "111"))
                .andExpect(status().isForbidden());
    }

    // ---------- using the token ----------

    @Test
    void homePage_withTheLoginCookie_isShownWithTheUsersName() throws Exception {
        Cookie cookie = loginAs("mercury@gmail.com", "111");

        mockMvc.perform(get("/").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("Mercury")));
    }

    @Test
    void homePage_withABearerToken_isShown() throws Exception {
        Cookie cookie = loginAs("mercury@gmail.com", "111");

        mockMvc.perform(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + cookie.getValue()))
                .andExpect(status().isOk());
    }

    @Test
    void homePage_withAGarbageBearerToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void homePage_withATamperedCookie_redirectsToLoginAndRemovesTheCookie() throws Exception {
        Cookie cookie = loginAs("mercury@gmail.com", "111");
        Cookie tampered = new Cookie(JWT_COOKIE, cookie.getValue() + "x");

        mockMvc.perform(get("/").cookie(tampered))
                .andExpect(redirectedUrl("/login"))
                .andExpect(cookie().maxAge(JWT_COOKIE, 0));
    }

    @Test
    void loginPage_forAUserWhoIsLoggedIn_redirectsHome() throws Exception {
        Cookie cookie = loginAs("mercury@gmail.com", "111");

        mockMvc.perform(get("/login").cookie(cookie))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void noServerSessionIsEverCreated() throws Exception {
        MvcResult loginPage = mockMvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        assertNull(loginPage.getRequest().getSession(false), "showing the login form must not create a session");

        Csrf csrf = fetchCsrf();
        MvcResult login = mockMvc.perform(loginRequest(csrf, "mercury@gmail.com", "111"))
                .andExpect(redirectedUrl("/"))
                .andReturn();
        assertNull(login.getRequest().getSession(false), "logging in must not create a session");

        Cookie jwt = login.getResponse().getCookie(JWT_COOKIE);
        MvcResult home = mockMvc.perform(get("/").cookie(jwt)).andExpect(status().isOk()).andReturn();
        assertNull(home.getRequest().getSession(false), "the application must stay stateless");
    }

    // ---------- logout ----------

    @Test
    void logout_removesTheCookieAndShowsTheLoginPage() throws Exception {
        Cookie jwt = loginAs("mercury@gmail.com", "111");
        Csrf csrf = fetchCsrf();

        mockMvc.perform(post("/logout").cookie(jwt, csrf.cookie()).param("_csrf", csrf.token()))
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(cookie().maxAge(JWT_COOKIE, 0));
    }

    @Test
    void logout_withoutCsrfToken_isForbidden() throws Exception {
        Cookie jwt = loginAs("mercury@gmail.com", "111");

        mockMvc.perform(post("/logout").cookie(jwt))
                .andExpect(status().isForbidden());
    }
}

package com.epam.rd.autocode.assessment.appliances.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.epam.rd.autocode.assessment.appliances.support.AuthTestSupport.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * What each role SEES: the navigation bar and the buttons. (What each role may DO is checked in
 * RoleAccessIntegrationTest.) Needs thymeleaf-extras-springsecurity6 on the classpath.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NavbarIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private Cookie employee; // Phobos
    private Cookie client;   // Mercury

    @BeforeEach
    void logIn() throws Exception {
        employee = loginAsEmployee(mockMvc);
        client = loginAsClient(mockMvc);
    }

    // ---------- navigation ----------

    @Test
    void anEmployee_seesEveryMenuItem() throws Exception {
        mockMvc.perform(get("/").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/appliances\"")))
                .andExpect(content().string(containsString("href=\"/orders\"")))
                .andExpect(content().string(containsString("href=\"/manufacturers\"")))
                .andExpect(content().string(containsString("href=\"/clients\"")))
                .andExpect(content().string(containsString("href=\"/employees\"")))
                .andExpect(content().string(not(containsString("My orders"))));
    }

    @Test
    void aClient_seesOnlyTheirOwnMenuItems() throws Exception {
        mockMvc.perform(get("/").cookie(client))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/appliances\"")))
                .andExpect(content().string(containsString("href=\"/orders\"")))
                .andExpect(content().string(containsString("My orders")))
                .andExpect(content().string(not(containsString("href=\"/manufacturers\""))))
                .andExpect(content().string(not(containsString("href=\"/clients\""))))
                .andExpect(content().string(not(containsString("href=\"/employees\""))));
    }

    @Test
    void theMenu_isTheSameOnEveryPage() throws Exception {
        mockMvc.perform(get("/appliances").cookie(client))
                .andExpect(content().string(containsString("My orders")))
                .andExpect(content().string(not(containsString("href=\"/clients\""))));
        mockMvc.perform(get("/orders").cookie(client))
                .andExpect(content().string(not(containsString("href=\"/employees\""))));
        mockMvc.perform(get("/clients").cookie(employee))
                .andExpect(content().string(containsString("href=\"/employees\"")));
    }

    @Test
    void everyoneSees_theirNameAndALogoutButtonWithACsrfToken() throws Exception {
        mockMvc.perform(get("/").cookie(employee))
                .andExpect(content().string(containsString("Welcome, Phobos!")))
                .andExpect(content().string(containsString("action=\"/logout\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mockMvc.perform(get("/").cookie(client))
                .andExpect(content().string(containsString("Welcome, Mercury!")));
    }

    @Test
    void theMenu_hasAButtonForSmallScreens() throws Exception {
        mockMvc.perform(get("/").cookie(client))
                .andExpect(content().string(containsString("navbar-toggler")))
                .andExpect(content().string(containsString("bootstrap.bundle")));
    }

    @Test
    void theMenu_isTranslated() throws Exception {
        // the escapes spell the Ukrainian words for "Appliances" and "Log out"
        mockMvc.perform(get("/").param("lang", "uk").cookie(employee))
                .andExpect(content().string(containsString("\u0422\u0435\u0445\u043d\u0456\u043a\u0430")))
                .andExpect(content().string(containsString("\u0412\u0438\u0439\u0442\u0438")));
    }

    @Test
    void theLoginPage_hasNoMenu() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/clients\""))))
                .andExpect(content().string(not(containsString("action=\"/logout\""))));
    }

    @Test
    void theErrorPage_hasNoMenu_butAWayBackHome() throws Exception {
        mockMvc.perform(get("/orders/999999").cookie(client)) // 404 page rendered by the exception handler
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("href=\"/\"")))
                .andExpect(content().string(not(containsString("href=\"/clients\""))));
    }

    // ---------- buttons ----------

    @Test
    void theAppliancesList_offersOnlyOrderingToAClient() throws Exception {
        mockMvc.perform(get("/appliances").cookie(client))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/orders/new?applianceId=1")))
                .andExpect(content().string(not(containsString("/appliances/new"))))
                .andExpect(content().string(not(containsString("/appliances/1/edit"))))
                .andExpect(content().string(not(containsString("/appliances/1/delete"))));
    }

    @Test
    void theAppliancesList_offersEverythingToAnEmployee() throws Exception {
        mockMvc.perform(get("/appliances").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/orders/new?applianceId=1")))
                .andExpect(content().string(containsString("/appliances/new")))
                .andExpect(content().string(containsString("/appliances/1/edit")))
                .andExpect(content().string(containsString("/appliances/1/delete")));
    }

    @Test
    void theHomePage_showsTheManagementShortcutsOnlyToEmployees() throws Exception {
        // the shortcut buttons are the only "btn-outline-secondary" elements on the home page
        mockMvc.perform(get("/").cookie(employee))
                .andExpect(content().string(containsString("btn btn-outline-secondary")));
        mockMvc.perform(get("/").cookie(client))
                .andExpect(content().string(not(containsString("btn btn-outline-secondary"))));
    }
}

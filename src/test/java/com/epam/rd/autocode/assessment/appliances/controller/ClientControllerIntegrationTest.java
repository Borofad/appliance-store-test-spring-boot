package com.epam.rd.autocode.assessment.appliances.controller;

import com.epam.rd.autocode.assessment.appliances.model.Appliance;
import com.epam.rd.autocode.assessment.appliances.model.Client;
import com.epam.rd.autocode.assessment.appliances.model.OrderRow;
import com.epam.rd.autocode.assessment.appliances.model.Orders;
import com.epam.rd.autocode.assessment.appliances.repository.ApplianceRepository;
import com.epam.rd.autocode.assessment.appliances.repository.ClientRepository;
import com.epam.rd.autocode.assessment.appliances.repository.OrdersRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.epam.rd.autocode.assessment.appliances.support.AuthTestSupport.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The Clients pages end to end (real security, templates, validation messages, password hashing, seeded H2 data).
 * Every test is rolled back. Seed data: 8 clients (Mercury is id 1, password 111), 4 employees
 * (phobos@gmail.com, moon@gmail.com, ...). Nobody has an order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClientControllerIntegrationTest {

    private static final Map<String, String> NEW_CLIENT = Map.of(
            "name", "Test Client",
            "email", "test.client@example.com",
            "card", "9999-0000",
            "password", "Str0ng!Pass",
            "confirmPassword", "Str0ng!Pass");

    /** The edit form of seeded client 1 (Mercury) with an empty password, i.e. "keep the current one". */
    private static final Map<String, String> EDIT_MERCURY = Map.of(
            "name", "Mercury",
            "email", "mercury@gmail.com",
            "card", "5326-1111",
            "password", "",
            "confirmPassword", "");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private ApplianceRepository applianceRepository;
    @Autowired
    private OrdersRepository ordersRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Cookie employee;

    @BeforeEach
    void logIn() throws Exception {
        employee = loginAsEmployee(mockMvc);
    }

    private String html(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    /**
     * A POST with a complete form. Pairs of (field, value) given as overrides REPLACE the default value.
     * (A second .param() value would be ignored for numbers/enums and joined for text, so tests would lie.)
     */
    private MockHttpServletRequestBuilder form(String url, Map<String, String> defaults, String... overrides)
            throws Exception {
        Map<String, String> fields = new LinkedHashMap<>(defaults);
        for (int i = 0; i < overrides.length; i += 2) {
            fields.put(overrides[i], overrides[i + 1]);
        }
        MockHttpServletRequestBuilder request = authenticatedPost(mockMvc, employee, url);
        fields.forEach((field, value) -> request.param(field, value));
        return request;
    }

    private MockHttpServletRequestBuilder newClientForm(String... overrides) throws Exception {
        return form("/clients", NEW_CLIENT, overrides);
    }

    private MockHttpServletRequestBuilder editMercuryForm(String... overrides) throws Exception {
        return form("/clients/1", EDIT_MERCURY, overrides);
    }

    private void givePlacedAnOrder(Client client) {
        Appliance claw = applianceRepository.findById(1L).orElseThrow();
        OrderRow row = new OrderRow();
        row.setAppliance(claw);
        row.setNumber(1L);
        row.setAmount(claw.getPrice());
        Orders order = new Orders();
        order.setClient(client);
        order.getOrderRowSet().add(row);
        ordersRepository.saveAndFlush(order);
    }

    // ---------- list ----------

    @Test
    void list_withoutLogin_redirectsToTheLoginPage() throws Exception {
        mockMvc.perform(get("/clients"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void list_showsTheSeededClients_andNeverAPasswordHash() throws Exception {
        mockMvc.perform(get("/clients").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(view().name("client/clients"))
                .andExpect(content().string(containsString("Mercury")))
                .andExpect(content().string(containsString("neptune@gmail.com")))
                .andExpect(content().string(containsString("5326-1111")))
                .andExpect(content().string(not(containsString("$2a$"))))
                .andExpect(model().attribute("page", hasProperty("totalElements", is(8L))));
    }

    @Test
    void list_search_findsByName_ignoringCase() throws Exception {
        mockMvc.perform(get("/clients").param("query", "mER").cookie(employee))
                .andExpect(content().string(containsString("Mercury")))
                .andExpect(content().string(not(containsString("Venus"))));
    }

    @Test
    void list_search_findsByEmail() throws Exception {
        mockMvc.perform(get("/clients").param("query", "iamgod").cookie(employee))
                .andExpect(content().string(containsString("Mars")))
                .andExpect(content().string(not(containsString("Venus"))));
    }

    @Test
    void list_search_withNoMatch_showsTheNoDataMessage() throws Exception {
        mockMvc.perform(get("/clients").param("query", "zzzz").cookie(employee))
                .andExpect(content().string(containsString("No data to display")));
    }

    @Test
    void list_pagination_splitsTheResultIntoPages() throws Exception {
        MvcResult first = mockMvc.perform(get("/clients").param("size", "5").cookie(employee))
                .andExpect(model().attribute("page", hasProperty("totalPages", is(2))))
                .andReturn();
        assertTrue(html(first).contains("page=1"), "there must be a link to the second page");

        mockMvc.perform(get("/clients").param("size", "5").param("page", "1").cookie(employee))
                .andExpect(model().attribute("page", hasProperty("number", is(1))));
    }

    @Test
    void list_sortingByName_canBeReversed() throws Exception {
        MvcResult asc = mockMvc.perform(get("/clients")
                .param("sortField", "name").param("sortDir", "asc").cookie(employee)).andReturn();
        MvcResult desc = mockMvc.perform(get("/clients")
                .param("sortField", "name").param("sortDir", "desc").cookie(employee)).andReturn();

        assertTrue(html(asc).indexOf("Earth") < html(asc).indexOf("Venus"));
        assertTrue(html(desc).indexOf("Venus") < html(desc).indexOf("Earth"));
    }

    @Test
    void list_hostileSortAndSizeValues_doNotBreakThePage() throws Exception {
        mockMvc.perform(get("/clients")
                        .param("sortField", "password").param("sortDir", "sideways")
                        .param("size", "999999").param("page", "-4").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mercury")));
    }

    // ---------- create ----------

    @Test
    void newForm_showsAllFieldsAndThePasswordRules() throws Exception {
        mockMvc.perform(get("/clients/new").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(view().name("client/newClient"))
                .andExpect(content().string(containsString("Add client")))
                .andExpect(content().string(containsString("type=\"password\"")))
                .andExpect(content().string(containsString("At least 8 characters")))
                .andExpect(content().string(not(containsString("Leave empty to keep"))));
    }

    @Test
    void create_valid_savesTheClientWithAHashedPassword() throws Exception {
        mockMvc.perform(newClientForm())
                .andExpect(redirectedUrl("/clients?created"));

        Client saved = clientRepository.findByEmail("test.client@example.com").orElseThrow();
        assertEquals("Test Client", saved.getName());
        assertEquals("9999-0000", saved.getCard());
        assertNotEquals("Str0ng!Pass", saved.getPassword(), "the password must never be stored as plain text");
        assertTrue(saved.getPassword().startsWith("$2"), "BCrypt hash expected");
        assertTrue(passwordEncoder.matches("Str0ng!Pass", saved.getPassword()));
    }

    @Test
    void create_thenTheNewClientCanLogIn() throws Exception {
        mockMvc.perform(newClientForm()).andExpect(redirectedUrl("/clients?created"));

        Cookie newClientCookie = login(mockMvc, "test.client@example.com", "Str0ng!Pass");

        mockMvc.perform(get("/").cookie(newClientCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test Client")));
    }

    @Test
    void create_emptyForm_showsAnErrorOnEveryRequiredField() throws Exception {
        mockMvc.perform(newClientForm("name", "", "email", "", "card", "", "password", "", "confirmPassword", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("client/newClient"))
                .andExpect(model().attributeHasFieldErrors("client", "name", "email", "card", "password"))
                .andExpect(content().string(containsString("is-invalid")))
                .andExpect(content().string(containsString("Password is required")))
                .andExpect(content().string(containsString("Card number is required")));
    }

    @Test
    void create_weakPassword_isRejected() throws Exception {
        mockMvc.perform(newClientForm("password", "abcdefg1", "confirmPassword", "abcdefg1"))
                .andExpect(model().attributeHasFieldErrors("client", "password"))
                .andExpect(content().string(containsString("8 to 64 characters")));

        assertTrue(clientRepository.findByEmail("test.client@example.com").isEmpty());
    }

    @Test
    void create_passwordsThatDoNotMatch_showTheErrorOnTheConfirmationField() throws Exception {
        mockMvc.perform(newClientForm("confirmPassword", "Str0ng!Other"))
                .andExpect(model().attributeHasFieldErrors("client", "confirmPassword"))
                .andExpect(content().string(containsString("Passwords do not match")));
    }

    @Test
    void create_invalidEmail_isRejected() throws Exception {
        mockMvc.perform(newClientForm("email", "not-an-email"))
                .andExpect(model().attributeHasFieldErrors("client", "email"))
                .andExpect(content().string(containsString("Please enter a valid email address")));
    }

    @Test
    void create_emailOfAnotherClient_isRejected_ignoringCase() throws Exception {
        long before = clientRepository.count();

        mockMvc.perform(newClientForm("email", "MERCURY@gmail.com"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("client", "email"))
                .andExpect(content().string(containsString("This email is already registered")));

        assertEquals(before, clientRepository.count());
    }

    @Test
    void create_emailOfAnEmployee_isRejected() throws Exception {
        mockMvc.perform(newClientForm("email", "phobos@gmail.com"))
                .andExpect(model().attributeHasFieldErrors("client", "email"))
                .andExpect(content().string(containsString("This email is already registered")));
    }

    @Test
    void create_tooLongName_showsTheSizeError() throws Exception {
        mockMvc.perform(newClientForm("name", "x".repeat(101)))
                .andExpect(model().attributeHasFieldErrors("client", "name"))
                .andExpect(content().string(containsString("Name must not exceed 100 characters")));
    }

    @Test
    void create_failedSubmit_neverSendsThePasswordsBackToTheBrowser() throws Exception {
        MvcResult result = mockMvc.perform(newClientForm(
                        "password", "Aa1!Distinct-One", "confirmPassword", "Aa1!Distinct-Two"))
                .andExpect(status().isOk())
                .andReturn();

        assertFalse(html(result).contains("Distinct-One"));
        assertFalse(html(result).contains("Distinct-Two"));
    }

    @Test
    void create_errorsAreShownInUkrainian() throws Exception {
        // the escapes spell the Ukrainian word for "Email": the "email is required" message starts with it
        mockMvc.perform(newClientForm("email", "", "lang", "uk"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\u0415\u043b\u0435\u043a\u0442\u0440\u043e\u043d\u043d\u0430 \u043f\u043e\u0448\u0442\u0430")));
    }

    @Test
    void create_ignoresAnIdSentInTheForm() throws Exception {
        mockMvc.perform(newClientForm("id", "1"))
                .andExpect(redirectedUrl("/clients?created"));

        assertEquals("Mercury", clientRepository.findById(1L).orElseThrow().getName());
        assertTrue(clientRepository.findByEmail("test.client@example.com").isPresent());
    }

    @Test
    void create_withoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/clients").cookie(employee).param("name", "x"))
                .andExpect(status().isForbidden());
    }

    // ---------- edit ----------

    @Test
    void editForm_showsTheCurrentValues_butNeverThePasswordHash() throws Exception {
        mockMvc.perform(get("/clients/1/edit").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(view().name("client/newClient"))
                .andExpect(content().string(containsString("Edit client")))
                .andExpect(content().string(containsString("value=\"Mercury\"")))
                .andExpect(content().string(containsString("value=\"mercury@gmail.com\"")))
                .andExpect(content().string(containsString("Leave empty to keep the current password")))
                .andExpect(content().string(not(containsString("$2a$"))));
    }

    @Test
    void editForm_unknownId_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(get("/clients/999/edit").cookie(employee))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/error"));
    }

    @Test
    void update_withoutAPassword_keepsTheCurrentPassword() throws Exception {
        String hashBefore = clientRepository.findById(1L).orElseThrow().getPassword();

        mockMvc.perform(editMercuryForm("name", "Mercury Updated", "card", "1111-2222"))
                .andExpect(redirectedUrl("/clients?updated"));

        Client changed = clientRepository.findById(1L).orElseThrow();
        assertEquals("Mercury Updated", changed.getName());
        assertEquals("1111-2222", changed.getCard());
        assertEquals(hashBefore, changed.getPassword());
        login(mockMvc, "mercury@gmail.com", "111"); // the old password still works
    }

    @Test
    void update_withANewPassword_replacesIt() throws Exception {
        mockMvc.perform(editMercuryForm("password", "N3w!Password", "confirmPassword", "N3w!Password"))
                .andExpect(redirectedUrl("/clients?updated"));

        String hash = clientRepository.findById(1L).orElseThrow().getPassword();
        assertTrue(passwordEncoder.matches("N3w!Password", hash));
        assertFalse(passwordEncoder.matches("111", hash));
        login(mockMvc, "mercury@gmail.com", "N3w!Password");
    }

    @Test
    void update_keepingTheSameEmail_isNotADuplicateOfItself() throws Exception {
        mockMvc.perform(editMercuryForm())
                .andExpect(redirectedUrl("/clients?updated"));
    }

    @Test
    void update_toAnotherClientsEmail_isRejected() throws Exception {
        mockMvc.perform(editMercuryForm("email", "venus@gmail.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("client/newClient"))
                .andExpect(model().attributeHasFieldErrors("client", "email"))
                .andExpect(content().string(containsString("This email is already registered")))
                .andExpect(content().string(containsString("Edit client")));

        assertEquals("mercury@gmail.com", clientRepository.findById(1L).orElseThrow().getEmail());
    }

    @Test
    void update_toAnEmployeesEmail_isRejected() throws Exception {
        mockMvc.perform(editMercuryForm("email", "moon@gmail.com"))
                .andExpect(model().attributeHasFieldErrors("client", "email"));
    }

    @Test
    void update_weakNewPassword_isRejectedAndTheOldOneStays() throws Exception {
        String hashBefore = clientRepository.findById(1L).orElseThrow().getPassword();

        mockMvc.perform(editMercuryForm("password", "weak", "confirmPassword", "weak"))
                .andExpect(model().attributeHasFieldErrors("client", "password"))
                .andExpect(content().string(containsString("8 to 64 characters")));

        assertEquals(hashBefore, clientRepository.findById(1L).orElseThrow().getPassword());
    }

    @Test
    void update_newPasswordWithoutConfirmation_isRejected() throws Exception {
        mockMvc.perform(editMercuryForm("password", "N3w!Password"))
                .andExpect(model().attributeHasFieldErrors("client", "confirmPassword"))
                .andExpect(content().string(containsString("Passwords do not match")));
    }

    @Test
    void update_unknownId_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(form("/clients/999", EDIT_MERCURY, "email", "unused@example.com"))
                .andExpect(status().isNotFound());
    }

    // ---------- delete ----------

    @Test
    void delete_clientWithoutOrders_isRemoved() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/clients/2/delete"))
                .andExpect(redirectedUrl("/clients?deleted"));

        assertFalse(clientRepository.existsById(2L));
    }

    @Test
    void delete_clientWithOrders_isKeptAndTheFailureIsReported() throws Exception {
        givePlacedAnOrder(clientRepository.findById(1L).orElseThrow());

        mockMvc.perform(authenticatedPost(mockMvc, employee, "/clients/1/delete"))
                .andExpect(redirectedUrl("/clients?deleteFailed"));

        assertTrue(clientRepository.existsById(1L));
    }

    @Test
    void list_showsTheDeleteFailedMessage() throws Exception {
        mockMvc.perform(get("/clients").param("deleteFailed", "").cookie(employee))
                .andExpect(content().string(containsString("has orders and cannot be deleted")));
    }

    @Test
    void delete_unknownId_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/clients/999/delete"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_withoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/clients/2/delete").cookie(employee))
                .andExpect(status().isForbidden());
    }
}

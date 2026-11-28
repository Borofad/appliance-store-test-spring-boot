package com.epam.rd.autocode.assessment.appliances.controller;

import com.epam.rd.autocode.assessment.appliances.model.Appliance;
import com.epam.rd.autocode.assessment.appliances.model.Client;
import com.epam.rd.autocode.assessment.appliances.model.Employee;
import com.epam.rd.autocode.assessment.appliances.model.OrderRow;
import com.epam.rd.autocode.assessment.appliances.model.Orders;
import com.epam.rd.autocode.assessment.appliances.repository.ApplianceRepository;
import com.epam.rd.autocode.assessment.appliances.repository.ClientRepository;
import com.epam.rd.autocode.assessment.appliances.repository.EmployeeRepository;
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
 * The Employees pages end to end (real security, templates, validation messages, password hashing, seeded H2 data).
 * The navigation bar shows the logged-in employee's name (Phobos) at the top of every page, so tests that
 * tell table rows apart by name use Moon, who is NOT logged in.
 * Every test is rolled back. Seed data: 4 employees - Phobos (id 1, salle, the logged-in user in these tests),
 * Moon (2, salle), Deimos (3, security), Europa (4, security) - all with a demo password; 8 clients; no orders.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmployeeControllerIntegrationTest {

    private static final Map<String, String> NEW_EMPLOYEE = Map.of(
            "name", "Test Employee",
            "email", "test.employee@example.com",
            "department", "support",
            "password", "Str0ng!Pass",
            "confirmPassword", "Str0ng!Pass");

    /** The edit form of seeded employee 1 (Phobos, the logged-in user) with an empty password. */
    private static final Map<String, String> EDIT_PHOBOS = Map.of(
            "name", "Phobos",
            "email", "phobos@gmail.com",
            "department", "salle",
            "password", "",
            "confirmPassword", "");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private ApplianceRepository applianceRepository;
    @Autowired
    private OrdersRepository ordersRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Cookie employee; // logged in as Phobos

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

    private MockHttpServletRequestBuilder newEmployeeForm(String... overrides) throws Exception {
        return form("/employees", NEW_EMPLOYEE, overrides);
    }

    private MockHttpServletRequestBuilder editPhobosForm(String... overrides) throws Exception {
        return form("/employees/1", EDIT_PHOBOS, overrides);
    }

    /** An approved order, so that this employee "has approved orders". */
    private void giveApprovedAnOrder(Employee approver) {
        Client client = clientRepository.findByEmail("mercury@gmail.com").orElseThrow();
        Appliance claw = applianceRepository.findById(1L).orElseThrow();
        OrderRow row = new OrderRow();
        row.setAppliance(claw);
        row.setNumber(1L);
        row.setAmount(claw.getPrice());
        Orders order = new Orders();
        order.setClient(client);
        order.setEmployee(approver);
        order.setApproved(true);
        order.getOrderRowSet().add(row);
        ordersRepository.saveAndFlush(order);
    }

    // ---------- list ----------

    @Test
    void list_withoutLogin_redirectsToTheLoginPage() throws Exception {
        mockMvc.perform(get("/employees"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void list_showsTheSeededEmployees_andNeverAPasswordHash() throws Exception {
        mockMvc.perform(get("/employees").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(view().name("employee/employees"))
                .andExpect(content().string(containsString("Phobos")))
                .andExpect(content().string(containsString("europa@gmail.com")))
                .andExpect(content().string(containsString("security")))
                .andExpect(content().string(not(containsString("$2a$"))))
                .andExpect(model().attribute("page", hasProperty("totalElements", is(4L))));
    }

    @Test
    void list_hidesTheDeleteButtonOnTheLoggedInEmployeesOwnRowOnly() throws Exception {
        MvcResult result = mockMvc.perform(get("/employees").cookie(employee)).andExpect(status().isOk()).andReturn();

        assertFalse(html(result).contains("/employees/1/delete"), "no Delete for yourself (Phobos is id 1)");
        assertTrue(html(result).contains("/employees/2/delete"));
        assertTrue(html(result).contains("/employees/4/delete"));
    }

    @Test
    void list_search_findsByName_ignoringCase() throws Exception {
        mockMvc.perform(get("/employees").param("query", "dEiM").cookie(employee))
                .andExpect(content().string(containsString("Deimos")))
                .andExpect(content().string(not(containsString("Moon"))));
    }

    @Test
    void list_search_findsByEmail() throws Exception {
        mockMvc.perform(get("/employees").param("query", "europa@").cookie(employee))
                .andExpect(content().string(containsString("Europa")))
                .andExpect(content().string(not(containsString("Moon"))));
    }

    @Test
    void list_search_findsByDepartment() throws Exception {
        mockMvc.perform(get("/employees").param("query", "secur").cookie(employee))
                .andExpect(content().string(containsString("Deimos")))
                .andExpect(content().string(containsString("Europa")))
                .andExpect(content().string(not(containsString("Moon"))));
    }

    @Test
    void list_search_withNoMatch_showsTheNoDataMessage() throws Exception {
        mockMvc.perform(get("/employees").param("query", "zzzz").cookie(employee))
                .andExpect(content().string(containsString("No data to display")));
    }

    @Test
    void list_pagination_splitsTheResultIntoPages() throws Exception {
        employeeRepository.saveAndFlush(new Employee(null, "Extra One", "extra1@example.com", "HASH", "ops"));
        employeeRepository.saveAndFlush(new Employee(null, "Extra Two", "extra2@example.com", "HASH", "ops"));

        MvcResult first = mockMvc.perform(get("/employees").param("size", "5").cookie(employee))
                .andExpect(model().attribute("page", hasProperty("totalPages", is(2))))
                .andReturn();
        assertTrue(html(first).contains("page=1"), "there must be a link to the second page");

        mockMvc.perform(get("/employees").param("size", "5").param("page", "1").cookie(employee))
                .andExpect(model().attribute("page", hasProperty("number", is(1))));
    }

    @Test
    void list_sortingByName_canBeReversed() throws Exception {
        MvcResult asc = mockMvc.perform(get("/employees")
                .param("sortField", "name").param("sortDir", "asc").cookie(employee)).andReturn();
        MvcResult desc = mockMvc.perform(get("/employees")
                .param("sortField", "name").param("sortDir", "desc").cookie(employee)).andReturn();

        assertTrue(html(asc).indexOf("Deimos") < html(asc).indexOf("Moon"));
        assertTrue(html(desc).indexOf("Moon") < html(desc).indexOf("Deimos"));
    }

    @Test
    void list_sortingByDepartment_works() throws Exception {
        // "salle" (Moon) sorts before "security" (Deimos)
        MvcResult asc = mockMvc.perform(get("/employees")
                        .param("sortField", "department").param("sortDir", "asc").cookie(employee))
                .andExpect(status().isOk()).andReturn();

        assertTrue(html(asc).indexOf("Moon") < html(asc).indexOf("Deimos"));
    }

    @Test
    void list_hostileSortAndSizeValues_doNotBreakThePage() throws Exception {
        mockMvc.perform(get("/employees")
                        .param("sortField", "password").param("sortDir", "sideways")
                        .param("size", "999999").param("page", "-4").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Phobos")));
    }

    // ---------- create ----------

    @Test
    void newForm_showsAllFieldsAndThePasswordRules() throws Exception {
        mockMvc.perform(get("/employees/new").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(view().name("employee/newEmployee"))
                .andExpect(content().string(containsString("Add employee")))
                .andExpect(content().string(containsString("Department")))
                .andExpect(content().string(containsString("type=\"password\"")))
                .andExpect(content().string(containsString("At least 8 characters")))
                .andExpect(content().string(not(containsString("Leave empty to keep"))));
    }

    @Test
    void create_valid_savesTheEmployeeWithAHashedPassword() throws Exception {
        mockMvc.perform(newEmployeeForm())
                .andExpect(redirectedUrl("/employees?created"));

        Employee saved = employeeRepository.findByEmail("test.employee@example.com").orElseThrow();
        assertEquals("Test Employee", saved.getName());
        assertEquals("support", saved.getDepartment());
        assertNotEquals("Str0ng!Pass", saved.getPassword(), "the password must never be stored as plain text");
        assertTrue(saved.getPassword().startsWith("$2"), "BCrypt hash expected");
        assertTrue(passwordEncoder.matches("Str0ng!Pass", saved.getPassword()));
    }

    @Test
    void create_thenTheNewEmployeeCanLogIn() throws Exception {
        mockMvc.perform(newEmployeeForm()).andExpect(redirectedUrl("/employees?created"));

        Cookie newEmployeeCookie = login(mockMvc, "test.employee@example.com", "Str0ng!Pass");

        mockMvc.perform(get("/").cookie(newEmployeeCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test Employee")));
    }

    @Test
    void create_emptyForm_showsAnErrorOnEveryRequiredField() throws Exception {
        mockMvc.perform(newEmployeeForm("name", "", "email", "", "department", "", "password", "", "confirmPassword", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("employee/newEmployee"))
                .andExpect(model().attributeHasFieldErrors("employee", "name", "email", "department", "password"))
                .andExpect(content().string(containsString("is-invalid")))
                .andExpect(content().string(containsString("Password is required")))
                .andExpect(content().string(containsString("Department is required")));
    }

    @Test
    void create_weakPassword_isRejected() throws Exception {
        mockMvc.perform(newEmployeeForm("password", "abcdefg1", "confirmPassword", "abcdefg1"))
                .andExpect(model().attributeHasFieldErrors("employee", "password"))
                .andExpect(content().string(containsString("8 to 64 characters")));

        assertTrue(employeeRepository.findByEmail("test.employee@example.com").isEmpty());
    }

    @Test
    void create_passwordsThatDoNotMatch_showTheErrorOnTheConfirmationField() throws Exception {
        mockMvc.perform(newEmployeeForm("confirmPassword", "Str0ng!Other"))
                .andExpect(model().attributeHasFieldErrors("employee", "confirmPassword"))
                .andExpect(content().string(containsString("Passwords do not match")));
    }

    @Test
    void create_invalidEmail_isRejected() throws Exception {
        mockMvc.perform(newEmployeeForm("email", "not-an-email"))
                .andExpect(model().attributeHasFieldErrors("employee", "email"))
                .andExpect(content().string(containsString("Please enter a valid email address")));
    }

    @Test
    void create_emailOfAnotherEmployee_isRejected_ignoringCase() throws Exception {
        long before = employeeRepository.count();

        mockMvc.perform(newEmployeeForm("email", "PHOBOS@gmail.com"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("employee", "email"))
                .andExpect(content().string(containsString("This email is already registered")));

        assertEquals(before, employeeRepository.count());
    }

    @Test
    void create_emailOfAClient_isRejected() throws Exception {
        mockMvc.perform(newEmployeeForm("email", "mercury@gmail.com"))
                .andExpect(model().attributeHasFieldErrors("employee", "email"))
                .andExpect(content().string(containsString("This email is already registered")));
    }

    @Test
    void create_tooLongNameAndDepartment_showTheSizeErrors() throws Exception {
        mockMvc.perform(newEmployeeForm("name", "x".repeat(101), "department", "y".repeat(101)))
                .andExpect(model().attributeHasFieldErrors("employee", "name", "department"))
                .andExpect(content().string(containsString("Name must not exceed 100 characters")))
                .andExpect(content().string(containsString("Department must not exceed 100 characters")));
    }

    @Test
    void create_failedSubmit_neverSendsThePasswordsBackToTheBrowser() throws Exception {
        MvcResult result = mockMvc.perform(newEmployeeForm(
                        "password", "Aa1!Distinct-One", "confirmPassword", "Aa1!Distinct-Two"))
                .andExpect(status().isOk())
                .andReturn();

        assertFalse(html(result).contains("Distinct-One"));
        assertFalse(html(result).contains("Distinct-Two"));
    }

    @Test
    void create_errorsAreShownInUkrainian() throws Exception {
        // the escapes spell the Ukrainian word for "Email": the "email is required" message starts with it
        mockMvc.perform(newEmployeeForm("email", "", "lang", "uk"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\u0415\u043b\u0435\u043a\u0442\u0440\u043e\u043d\u043d\u0430 \u043f\u043e\u0448\u0442\u0430")));
    }

    @Test
    void create_ignoresAnIdSentInTheForm() throws Exception {
        mockMvc.perform(newEmployeeForm("id", "1"))
                .andExpect(redirectedUrl("/employees?created"));

        assertEquals("Phobos", employeeRepository.findById(1L).orElseThrow().getName());
        assertTrue(employeeRepository.findByEmail("test.employee@example.com").isPresent());
    }

    @Test
    void create_withoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/employees").cookie(employee).param("name", "x"))
                .andExpect(status().isForbidden());
    }

    // ---------- edit ----------

    @Test
    void editForm_showsTheCurrentValues_butNeverThePasswordHash() throws Exception {
        mockMvc.perform(get("/employees/1/edit").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(view().name("employee/newEmployee"))
                .andExpect(content().string(containsString("Edit employee")))
                .andExpect(content().string(containsString("value=\"Phobos\"")))
                .andExpect(content().string(containsString("value=\"phobos@gmail.com\"")))
                .andExpect(content().string(containsString("value=\"salle\"")))
                .andExpect(content().string(containsString("Leave empty to keep the current password")))
                .andExpect(content().string(not(containsString("$2a$"))));
    }

    @Test
    void editForm_unknownId_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(get("/employees/999/edit").cookie(employee))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/error"));
    }

    @Test
    void update_withoutAPassword_keepsTheCurrentPassword() throws Exception {
        String hashBefore = employeeRepository.findById(1L).orElseThrow().getPassword();

        mockMvc.perform(editPhobosForm("name", "Phobos Updated", "department", "ops"))
                .andExpect(redirectedUrl("/employees?updated"));

        Employee changed = employeeRepository.findById(1L).orElseThrow();
        assertEquals("Phobos Updated", changed.getName());
        assertEquals("ops", changed.getDepartment());
        assertEquals(hashBefore, changed.getPassword());
        login(mockMvc, "phobos@gmail.com", "111"); // the old password still works
    }

    @Test
    void update_withANewPassword_replacesIt() throws Exception {
        mockMvc.perform(editPhobosForm("password", "N3w!Password", "confirmPassword", "N3w!Password"))
                .andExpect(redirectedUrl("/employees?updated"));

        String hash = employeeRepository.findById(1L).orElseThrow().getPassword();
        assertTrue(passwordEncoder.matches("N3w!Password", hash));
        assertFalse(passwordEncoder.matches("111", hash));
        login(mockMvc, "phobos@gmail.com", "N3w!Password");
    }

    @Test
    void update_ofYourOwnProfile_keepsYouLoggedIn() throws Exception {
        mockMvc.perform(editPhobosForm("name", "Phobos Updated")).andExpect(redirectedUrl("/employees?updated"));

        mockMvc.perform(get("/employees").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Phobos Updated")));
    }

    @Test
    void update_keepingTheSameEmail_isNotADuplicateOfItself() throws Exception {
        mockMvc.perform(editPhobosForm())
                .andExpect(redirectedUrl("/employees?updated"));
    }

    @Test
    void update_toAnotherEmployeesEmail_isRejected() throws Exception {
        mockMvc.perform(editPhobosForm("email", "moon@gmail.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("employee/newEmployee"))
                .andExpect(model().attributeHasFieldErrors("employee", "email"))
                .andExpect(content().string(containsString("This email is already registered")))
                .andExpect(content().string(containsString("Edit employee")));

        assertEquals("phobos@gmail.com", employeeRepository.findById(1L).orElseThrow().getEmail());
    }

    @Test
    void update_toAClientsEmail_isRejected() throws Exception {
        mockMvc.perform(editPhobosForm("email", "venus@gmail.com"))
                .andExpect(model().attributeHasFieldErrors("employee", "email"));
    }

    @Test
    void update_weakNewPassword_isRejectedAndTheOldOneStays() throws Exception {
        String hashBefore = employeeRepository.findById(1L).orElseThrow().getPassword();

        mockMvc.perform(editPhobosForm("password", "weak", "confirmPassword", "weak"))
                .andExpect(model().attributeHasFieldErrors("employee", "password"))
                .andExpect(content().string(containsString("8 to 64 characters")));

        assertEquals(hashBefore, employeeRepository.findById(1L).orElseThrow().getPassword());
    }

    @Test
    void update_newPasswordWithoutConfirmation_isRejected() throws Exception {
        mockMvc.perform(editPhobosForm("password", "N3w!Password"))
                .andExpect(model().attributeHasFieldErrors("employee", "confirmPassword"))
                .andExpect(content().string(containsString("Passwords do not match")));
    }

    @Test
    void update_unknownId_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(form("/employees/999", EDIT_PHOBOS, "email", "unused@example.com"))
                .andExpect(status().isNotFound());
    }

    // ---------- delete ----------

    @Test
    void delete_anotherEmployeeWithoutApprovedOrders_isRemoved() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/employees/4/delete"))
                .andExpect(redirectedUrl("/employees?deleted"));

        assertFalse(employeeRepository.existsById(4L));
    }

    @Test
    void delete_yourOwnAccount_isRefused() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/employees/1/delete"))
                .andExpect(redirectedUrl("/employees?deleteSelf"));

        assertTrue(employeeRepository.existsById(1L));
    }

    @Test
    void delete_employeeWhoApprovedAnOrder_isKeptAndTheFailureIsReported() throws Exception {
        giveApprovedAnOrder(employeeRepository.findById(2L).orElseThrow());

        mockMvc.perform(authenticatedPost(mockMvc, employee, "/employees/2/delete"))
                .andExpect(redirectedUrl("/employees?deleteFailed"));

        assertTrue(employeeRepository.existsById(2L));
    }

    @Test
    void list_showsTheDeleteFailedAndDeleteSelfMessages() throws Exception {
        mockMvc.perform(get("/employees").param("deleteFailed", "").cookie(employee))
                .andExpect(content().string(containsString("has approved orders and cannot be deleted")));
        mockMvc.perform(get("/employees").param("deleteSelf", "").cookie(employee))
                .andExpect(content().string(containsString("You cannot delete your own account")));
    }

    @Test
    void delete_unknownId_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/employees/999/delete"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_withoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/employees/4/delete").cookie(employee))
                .andExpect(status().isForbidden());
    }
}

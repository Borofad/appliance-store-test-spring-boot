package com.epam.rd.autocode.assessment.appliances.security;

import com.epam.rd.autocode.assessment.appliances.config.SecurityConfig;
import com.epam.rd.autocode.assessment.appliances.model.Client;
import com.epam.rd.autocode.assessment.appliances.model.OrderRow;
import com.epam.rd.autocode.assessment.appliances.model.Orders;
import com.epam.rd.autocode.assessment.appliances.model.Appliance;
import com.epam.rd.autocode.assessment.appliances.repository.ApplianceRepository;
import com.epam.rd.autocode.assessment.appliances.repository.ClientRepository;
import com.epam.rd.autocode.assessment.appliances.repository.EmployeeRepository;
import com.epam.rd.autocode.assessment.appliances.repository.ManufacturerRepository;
import com.epam.rd.autocode.assessment.appliances.repository.OrdersRepository;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static com.epam.rd.autocode.assessment.appliances.support.AuthTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Who may open what: the role rules of SecurityConfig, checked against the real application.
 *
 *   employees only ... manufacturers, clients, employees, and adding/changing/deleting appliances
 *   both roles ....... the home page, the appliance LIST, orders (a client sees only their own - see the order tests)
 *   anonymous ........ nothing except the login page; they are sent there
 *
 * Every test is rolled back. Seed data: 7 manufacturers, 7 appliances, 8 clients, 4 employees.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ManufacturerRepository manufacturerRepository;
    @Autowired
    private ApplianceRepository applianceRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private OrdersRepository ordersRepository;

    private Cookie employee; // Phobos
    private Cookie client;   // Mercury

    @BeforeEach
    void logIn() throws Exception {
        employee = loginAsEmployee(mockMvc);
        client = loginAsClient(mockMvc);
    }

    static Stream<String> employeeOnlyPages() {
        return Stream.of(
                "/manufacturers", "/manufacturers/new", "/manufacturers/1/edit",
                "/clients", "/clients/new", "/clients/1/edit",
                "/employees", "/employees/new", "/employees/1/edit",
                "/appliances/new", "/appliances/1/edit");
    }

    static Stream<String> pagesForBothRoles() {
        return Stream.of("/", "/appliances", "/orders", "/orders/new");
    }

    // ---------- pages ----------

    @ParameterizedTest
    @MethodSource("employeeOnlyPages")
    void employeeOnlyPages_areForbiddenForAClient(String url) throws Exception {
        mockMvc.perform(get(url).cookie(client))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("employeeOnlyPages")
    void employeeOnlyPages_areOpenToAnEmployee(String url) throws Exception {
        mockMvc.perform(get(url).cookie(employee))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource("employeeOnlyPages")
    void employeeOnlyPages_sendAnAnonymousVisitorToTheLoginPage(String url) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @MethodSource("pagesForBothRoles")
    void pagesForBothRoles_areOpenToAClientAndToAnEmployee(String url) throws Exception {
        mockMvc.perform(get(url).cookie(client)).andExpect(status().isOk());
        mockMvc.perform(get(url).cookie(employee)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource("pagesForBothRoles")
    void pagesForBothRoles_sendAnAnonymousVisitorToTheLoginPage(String url) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void aClientTokenSentAsABearerHeader_isForbidden_notUnauthorized() throws Exception {
        mockMvc.perform(get("/clients").header(HttpHeaders.AUTHORIZATION, "Bearer " + client.getValue()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownPage_isNotFound_forALoggedInUser() throws Exception {
        mockMvc.perform(get("/does-not-exist").cookie(client))
                .andExpect(status().isNotFound());
    }

    // ---------- changing data ----------

    /** {url, field, value, field, value, ...}: every change an employee can make that a client must not be able to. */
    private static final String[][] EMPLOYEE_ONLY_CHANGES = {
            {"/manufacturers", "name", "ZZ-Hack"},
            {"/manufacturers/1", "name", "Hacked"},
            {"/manufacturers/1/delete"},
            {"/appliances", "name", "Hack", "category", "SMALL", "model", "M", "manufacturerId", "1",
                    "powerType", "AC220", "power", "1", "price", "1.00"},
            {"/appliances/1", "name", "Hacked", "category", "SMALL", "model", "M", "manufacturerId", "1",
                    "powerType", "AC220", "power", "1", "price", "1.00"},
            {"/appliances/1/delete"},
            {"/clients", "name", "Hack", "email", "hack1@example.com", "card", "1",
                    "password", "Str0ng!Pass", "confirmPassword", "Str0ng!Pass"},
            {"/clients/2/delete"},
            {"/employees", "name", "Hack", "email", "hack2@example.com", "department", "x",
                    "password", "Str0ng!Pass", "confirmPassword", "Str0ng!Pass"},
            {"/employees/4/delete"},
    };

    @Test
    void aClientCannotChangeAnyEmployeeOnlyData_andNothingChanges() throws Exception {
        for (String[] change : EMPLOYEE_ONLY_CHANGES) {
            MockHttpServletRequestBuilder request = authenticatedPost(mockMvc, client, change[0]);
            for (int i = 1; i < change.length; i += 2) {
                request.param(change[i], change[i + 1]);
            }
            mockMvc.perform(request)
                    .andExpect(status().isForbidden());
        }

        assertEquals(7, manufacturerRepository.count());
        assertEquals("Samsung", manufacturerRepository.findById(1L).orElseThrow().getName());
        assertEquals(7, applianceRepository.count());
        assertEquals("Claw", applianceRepository.findById(1L).orElseThrow().getName());
        assertEquals(8, clientRepository.count());
        assertEquals(4, employeeRepository.count());
    }

    @Test
    void aClientCannotApproveAnOrder_andTheOrderStaysPending() throws Exception {
        Client mercury = clientRepository.findByEmail("mercury@gmail.com").orElseThrow();
        Appliance claw = applianceRepository.findById(1L).orElseThrow();
        OrderRow row = new OrderRow();
        row.setAppliance(claw);
        row.setNumber(1L);
        row.setAmount(claw.getPrice().multiply(BigDecimal.ONE));
        Orders order = new Orders();
        order.setClient(mercury);
        order.getOrderRowSet().add(row);
        ordersRepository.saveAndFlush(order);

        mockMvc.perform(authenticatedPost(mockMvc, client, "/orders/" + order.getId() + "/approve"))
                .andExpect(status().isForbidden());

        assertFalse(ordersRepository.findByIdWithRows(order.getId()).orElseThrow().getApproved());
    }

    @Test
    void anEmployeeCanStillChangeData() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/manufacturers").param("name", "ZZ-Temp"))
                .andExpect(redirectedUrl("/manufacturers?created"));

        assertTrue(manufacturerRepository.findByName("ZZ-Temp").isPresent());
    }

    // ---------- logging ----------

    @Test
    void aDeniedRequest_isLoggedAsASecurityEvent() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(SecurityConfig.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(get("/clients").cookie(client)).andExpect(status().isForbidden());
        } finally {
            logger.detachAppender(appender);
        }

        boolean logged = appender.list.stream().anyMatch(event ->
                event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("access denied")
                        && event.getFormattedMessage().contains("mercury@gmail.com")
                        && event.getFormattedMessage().contains("/clients"));
        assertTrue(logged, "the denied request must be logged as a WARN security event");
    }
}

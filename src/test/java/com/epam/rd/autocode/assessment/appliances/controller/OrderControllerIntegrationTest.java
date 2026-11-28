package com.epam.rd.autocode.assessment.appliances.controller;

import com.epam.rd.autocode.assessment.appliances.model.Appliance;
import com.epam.rd.autocode.assessment.appliances.model.Client;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.epam.rd.autocode.assessment.appliances.support.AuthTestSupport.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The Orders pages end to end: real security (login cookie, CSRF), real templates, validation messages,
 * the ownership rules and the seeded H2 data. Every test is rolled back.
 *
 * The navigation bar shows the logged-in user's name at the top of every page, so tests that check the
 * "approved by" name look at the order as the client (Mercury), not as the employee (Phobos).
 *
 * Seed data used here: employee Phobos (id 1, logged in as "employee"); clients Mercury (id 1, logged in as
 * "mercury") and Venus (id 2); appliances Claw (id 1, price 1.01, Samsung), Bane (id 2, 2.01), Ecu (id 3, 3.01).
 * No orders exist at the start. Orders are created through the repositories or through the real form.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderControllerIntegrationTest {

    private static final Pattern CREATED_URL = Pattern.compile("^/orders/(\\d+)\\?created$");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrdersRepository ordersRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private ApplianceRepository applianceRepository;

    private Cookie employee;  // Phobos
    private Cookie mercury;   // client 1

    @BeforeEach
    void logIn() throws Exception {
        employee = loginAsEmployee(mockMvc);
        mercury = loginAsClient(mockMvc);
    }

    // ---------- helpers ----------

    private Cookie venusCookie() throws Exception {
        return login(mockMvc, "venus@gmail.com", "222");
    }

    private Client mercuryClient() {
        return clientRepository.findByEmail("mercury@gmail.com").orElseThrow();
    }

    private Client venusClient() {
        return clientRepository.findByEmail("venus@gmail.com").orElseThrow();
    }

    /** A pending order with one row, saved straight into the database. */
    private Orders placeOrder(Client client, long applianceId, long quantity) {
        Appliance appliance = applianceRepository.findById(applianceId).orElseThrow();
        OrderRow row = new OrderRow();
        row.setAppliance(appliance);
        row.setNumber(quantity);
        row.setAmount(appliance.getPrice().multiply(BigDecimal.valueOf(quantity)));
        Orders order = new Orders();
        order.setClient(client);
        order.getOrderRowSet().add(row);
        return ordersRepository.saveAndFlush(order);
    }

    private Orders placeApprovedOrder(Client client, long applianceId, long quantity) {
        Orders order = placeOrder(client, applianceId, quantity);
        order.setEmployee(employeeRepository.findById(1L).orElseThrow());
        order.setApproved(true);
        return ordersRepository.saveAndFlush(order);
    }

    /** A POST with the login cookie, the CSRF cookie and the CSRF field, plus pairs of (field, value). */
    private MockHttpServletRequestBuilder postAs(Cookie jwt, String url, String... fields) throws Exception {
        MockHttpServletRequestBuilder request = authenticatedPost(mockMvc, jwt, url);
        for (int i = 0; i < fields.length; i += 2) {
            request.param(fields[i], fields[i + 1]);
        }
        return request;
    }

    private long createdOrderId(MvcResult result) {
        String url = result.getResponse().getRedirectedUrl();
        assertNotNull(url, "a successful save must redirect");
        Matcher matcher = CREATED_URL.matcher(url);
        assertTrue(matcher.matches(), "unexpected redirect: " + url);
        return Long.parseLong(matcher.group(1));
    }

    private Orders reload(long id) {
        return ordersRepository.findByIdWithRows(id).orElseThrow();
    }

    private String html(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private String link(Orders order) {
        return "href=\"/orders/" + order.getId() + "\"";
    }

    // ---------- list ----------

    @Test
    void list_withoutLogin_redirectsToTheLoginPage() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void list_forAnEmployee_showsEveryClientsOrders() throws Exception {
        Orders ofMercury = placeOrder(mercuryClient(), 1, 1);
        Orders ofVenus = placeOrder(venusClient(), 2, 1);

        mockMvc.perform(get("/orders").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(view().name("order/orders"))
                .andExpect(content().string(containsString(link(ofMercury))))
                .andExpect(content().string(containsString(link(ofVenus))))
                .andExpect(content().string(containsString("Mercury")))
                .andExpect(content().string(containsString("Venus")))
                .andExpect(model().attribute("employeeView", true))
                .andExpect(model().attribute("page", hasProperty("totalElements", is(2L))));
    }

    @Test
    void list_forAClient_showsOnlyTheirOwnOrders() throws Exception {
        Orders ofMercury = placeOrder(mercuryClient(), 1, 1);
        Orders ofVenus = placeOrder(venusClient(), 2, 1);

        mockMvc.perform(get("/orders").cookie(mercury))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(link(ofMercury))))
                .andExpect(content().string(not(containsString(link(ofVenus)))))
                .andExpect(model().attribute("employeeView", false))
                .andExpect(model().attribute("page", hasProperty("totalElements", is(1L))));
    }

    @Test
    void list_withNoOrders_showsTheNoDataMessage() throws Exception {
        mockMvc.perform(get("/orders").cookie(mercury))
                .andExpect(content().string(containsString("No data to display")));
    }

    @Test
    void list_showsStatusApproverAndTotal() throws Exception {
        placeOrder(mercuryClient(), 1, 3);                 // 3 x 1.01 = 3.03, pending
        placeApprovedOrder(mercuryClient(), 2, 1);         // approved by Phobos

        // seen by the client, so "Phobos" can only come from the "approved by" column (the navbar shows "Mercury")
        mockMvc.perform(get("/orders").cookie(mercury))
                .andExpect(content().string(containsString("Pending")))
                .andExpect(content().string(containsString("Approved")))
                .andExpect(content().string(containsString("3.03")))
                .andExpect(content().string(containsString("Phobos")));
    }

    @Test
    void list_pagination_splitsTheResultIntoPages() throws Exception {
        for (int i = 0; i < 6; i++) {
            placeOrder(mercuryClient(), 1, 1);
        }

        MvcResult first = mockMvc.perform(get("/orders").param("size", "5").cookie(employee))
                .andExpect(model().attribute("page", hasProperty("totalPages", is(2))))
                .andReturn();
        assertTrue(html(first).contains("page=1"), "there must be a link to the second page");

        mockMvc.perform(get("/orders").param("size", "5").param("page", "1").cookie(employee))
                .andExpect(model().attribute("page", hasProperty("number", is(1))));
    }

    @Test
    void list_isNewestFirstByDefault_andCanBeReversed() throws Exception {
        Orders older = placeOrder(mercuryClient(), 1, 1);
        Orders newer = placeOrder(mercuryClient(), 1, 1);

        MvcResult byDefault = mockMvc.perform(get("/orders").cookie(employee)).andReturn();
        MvcResult ascending = mockMvc.perform(get("/orders")
                .param("sortField", "id").param("sortDir", "asc").cookie(employee)).andReturn();

        assertTrue(html(byDefault).indexOf(link(newer)) < html(byDefault).indexOf(link(older)));
        assertTrue(html(ascending).indexOf(link(older)) < html(ascending).indexOf(link(newer)));
    }

    @Test
    void list_canBeSortedByTheClientsName() throws Exception {
        Orders ofVenus = placeOrder(venusClient(), 1, 1);
        Orders ofMercury = placeOrder(mercuryClient(), 1, 1);

        MvcResult asc = mockMvc.perform(get("/orders")
                        .param("sortField", "client.name").param("sortDir", "asc").cookie(employee))
                .andExpect(status().isOk()).andReturn();
        MvcResult desc = mockMvc.perform(get("/orders")
                        .param("sortField", "client.name").param("sortDir", "desc").cookie(employee))
                .andExpect(status().isOk()).andReturn();

        assertTrue(html(asc).indexOf(link(ofMercury)) < html(asc).indexOf(link(ofVenus)));
        assertTrue(html(desc).indexOf(link(ofVenus)) < html(desc).indexOf(link(ofMercury)));
    }

    @Test
    void list_canBeSortedByStatus() throws Exception {
        placeOrder(mercuryClient(), 1, 1);
        placeApprovedOrder(venusClient(), 2, 1);

        mockMvc.perform(get("/orders").param("sortField", "approved").param("sortDir", "desc").cookie(employee))
                .andExpect(status().isOk());
    }

    @Test
    void list_hostileSortAndSizeValues_doNotBreakThePage() throws Exception {
        placeOrder(mercuryClient(), 1, 1);

        mockMvc.perform(get("/orders")
                        .param("sortField", "client.password").param("sortDir", "sideways")
                        .param("size", "999999").param("page", "-4").cookie(employee))
                .andExpect(status().isOk());
    }

    @Test
    void list_offersEditAndDeleteOnlyForPendingOrders() throws Exception {
        Orders pending = placeOrder(mercuryClient(), 1, 1);
        Orders approved = placeApprovedOrder(mercuryClient(), 2, 1);

        MvcResult result = mockMvc.perform(get("/orders").cookie(mercury)).andExpect(status().isOk()).andReturn();

        assertTrue(html(result).contains("/orders/" + pending.getId() + "/edit"));
        assertTrue(html(result).contains("/orders/" + pending.getId() + "/delete"));
        assertFalse(html(result).contains("/orders/" + approved.getId() + "/edit"));
        assertFalse(html(result).contains("/orders/" + approved.getId() + "/delete"));
    }

    @Test
    void list_offersApprovalOnlyToEmployees_andOnlyForPendingOrders() throws Exception {
        Orders pending = placeOrder(mercuryClient(), 1, 1);
        Orders approved = placeApprovedOrder(mercuryClient(), 2, 1);

        MvcResult forEmployee = mockMvc.perform(get("/orders").cookie(employee)).andReturn();
        MvcResult forClient = mockMvc.perform(get("/orders").cookie(mercury)).andReturn();

        assertTrue(html(forEmployee).contains("/orders/" + pending.getId() + "/approve"));
        assertFalse(html(forEmployee).contains("/orders/" + approved.getId() + "/approve"));
        assertFalse(html(forClient).contains("/approve"));
    }

    @Test
    void list_showsTheDeletedMessage() throws Exception {
        mockMvc.perform(get("/orders").param("deleted", "").cookie(mercury))
                .andExpect(content().string(containsString("Deleted successfully")));
    }

    // ---------- details ----------

    @Test
    void details_showTheRowsAmountsAndTotal() throws Exception {
        Orders order = placeOrder(mercuryClient(), 1, 3);

        mockMvc.perform(get("/orders/" + order.getId()).cookie(employee))
                .andExpect(status().isOk())
                .andExpect(view().name("order/viewOrder"))
                .andExpect(content().string(containsString("Claw")))
                .andExpect(content().string(containsString("3.03")))
                .andExpect(content().string(containsString("Mercury")))
                .andExpect(content().string(containsString("Pending")));
    }

    @Test
    void details_aClientCanOpenTheirOwnOrder() throws Exception {
        Orders order = placeOrder(mercuryClient(), 1, 1);

        mockMvc.perform(get("/orders/" + order.getId()).cookie(mercury))
                .andExpect(status().isOk());
    }

    @Test
    void details_aClientCannotOpenAnotherClientsOrder_itLooksLikeItDoesNotExist() throws Exception {
        Orders ofVenus = placeOrder(venusClient(), 1, 1);

        mockMvc.perform(get("/orders/" + ofVenus.getId()).cookie(mercury))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/error"));
    }

    @Test
    void details_unknownOrder_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(get("/orders/999999").cookie(employee))
                .andExpect(status().isNotFound());
    }

    @Test
    void details_ofAPendingOrder_offersEditDeleteAndApproval_toAnEmployee() throws Exception {
        Orders order = placeOrder(mercuryClient(), 1, 1);

        MvcResult result = mockMvc.perform(get("/orders/" + order.getId()).cookie(employee)).andReturn();

        assertTrue(html(result).contains("/orders/" + order.getId() + "/edit"));
        assertTrue(html(result).contains("/orders/" + order.getId() + "/delete"));
        assertTrue(html(result).contains("/orders/" + order.getId() + "/approve"));
    }

    @Test
    void details_ofAPendingOrder_offersNoApproval_toTheClient() throws Exception {
        Orders order = placeOrder(mercuryClient(), 1, 1);

        MvcResult result = mockMvc.perform(get("/orders/" + order.getId()).cookie(mercury)).andReturn();

        assertTrue(html(result).contains("/orders/" + order.getId() + "/edit"));
        assertFalse(html(result).contains("/approve"));
    }

    @Test
    void details_ofAnApprovedOrder_offersNoChanges() throws Exception {
        Orders order = placeApprovedOrder(mercuryClient(), 1, 1);

        MvcResult result = mockMvc.perform(get("/orders/" + order.getId()).cookie(employee))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Approved")))
                .andReturn();

        assertFalse(html(result).contains("/edit"));
        assertFalse(html(result).contains("/delete"));
        assertFalse(html(result).contains("/approve"));
    }

    @Test
    void details_ofAnApprovedOrder_namesTheApproverToTheClient() throws Exception {
        Orders order = placeApprovedOrder(mercuryClient(), 1, 1);

        // seen by the client, so "Phobos" can only come from the "employee" line (the navbar shows "Mercury")
        mockMvc.perform(get("/orders/" + order.getId()).cookie(mercury))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Phobos")));
    }

    @Test
    void details_showTheResultMessages() throws Exception {
        Orders order = placeOrder(mercuryClient(), 1, 1);

        mockMvc.perform(get("/orders/" + order.getId()).param("created", "").cookie(mercury))
                .andExpect(content().string(containsString("Created successfully")));
        mockMvc.perform(get("/orders/" + order.getId()).param("approved", "").cookie(mercury))
                .andExpect(content().string(containsString("Order approved successfully")));
        mockMvc.perform(get("/orders/" + order.getId()).param("alreadyApproved", "").cookie(mercury))
                .andExpect(content().string(containsString("has already been approved")));
    }

    // ---------- new form ----------

    @Test
    void newForm_withoutLogin_redirectsToTheLoginPage() throws Exception {
        mockMvc.perform(get("/orders/new"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void newForm_forAnEmployee_offersTheClientsAndTheAppliancesWithPrices() throws Exception {
        mockMvc.perform(get("/orders/new").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(view().name("order/newOrder"))
                .andExpect(content().string(containsString("Create order")))
                .andExpect(content().string(containsString("id=\"clientId\"")))
                .andExpect(content().string(containsString("Venus")))
                .andExpect(content().string(containsString("Claw (Samsung) - 1.01")));
    }

    @Test
    void newForm_forAClient_hasNoClientDropdown() throws Exception {
        mockMvc.perform(get("/orders/new").cookie(mercury))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"clientId\""))))
                .andExpect(content().string(not(containsString("Venus"))))
                .andExpect(content().string(containsString("Claw (Samsung) - 1.01")));
    }

    @Test
    void newForm_preselectsTheApplianceChosenOnTheAppliancesPage() throws Exception {
        mockMvc.perform(get("/orders/new").param("applianceId", "3").cookie(mercury))
                .andExpect(model().attribute("order",
                        hasProperty("rows", hasItem(hasProperty("applianceId", is(3L))))));
    }

    @Test
    void theAppliancesPage_linksToTheOrderForm() throws Exception {
        mockMvc.perform(get("/appliances").cookie(mercury))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/orders/new?applianceId=1")));
    }

    // ---------- create ----------

    @Test
    void create_byAClient_savesAPendingOrderForThemselves_withTheCalculatedAmount() throws Exception {
        MvcResult result = mockMvc.perform(postAs(mercury, "/orders",
                        "rows[0].applianceId", "1", "rows[0].number", "2"))
                .andReturn();

        Orders saved = reload(createdOrderId(result));
        assertEquals("Mercury", saved.getClient().getName());
        assertFalse(saved.getApproved());
        assertNull(saved.getEmployee());
        assertEquals(1, saved.getOrderRowSet().size());
        OrderRow row = saved.getOrderRowSet().iterator().next();
        assertEquals(1L, row.getAppliance().getId());
        assertEquals(2L, row.getNumber());
        assertEquals(0, new BigDecimal("2.02").compareTo(row.getAmount()));
    }

    @Test
    void create_byAClient_cannotOrderOnBehalfOfAnotherClient() throws Exception {
        MvcResult result = mockMvc.perform(postAs(mercury, "/orders",
                        "clientId", String.valueOf(venusClient().getId()),
                        "rows[0].applianceId", "1", "rows[0].number", "1"))
                .andReturn();

        assertEquals("Mercury", reload(createdOrderId(result)).getClient().getName());
    }

    @Test
    void create_byAnEmployee_isForTheChosenClient() throws Exception {
        MvcResult result = mockMvc.perform(postAs(employee, "/orders",
                        "clientId", String.valueOf(venusClient().getId()),
                        "rows[0].applianceId", "2", "rows[0].number", "1"))
                .andReturn();

        assertEquals("Venus", reload(createdOrderId(result)).getClient().getName());
    }

    @Test
    void create_byAnEmployee_withoutAClient_showsTheErrorOnTheClientField() throws Exception {
        long before = ordersRepository.count();

        mockMvc.perform(postAs(employee, "/orders", "rows[0].applianceId", "1", "rows[0].number", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("order/newOrder"))
                .andExpect(model().attributeHasFieldErrors("order", "clientId"))
                .andExpect(content().string(containsString("Client is required")))
                .andExpect(content().string(containsString("Venus"))); // the dropdowns are filled again

        assertEquals(before, ordersRepository.count());
    }

    @Test
    void create_rowWithoutApplianceAndQuantity_showsTheErrorsOnTheRow() throws Exception {
        long before = ordersRepository.count();

        mockMvc.perform(postAs(mercury, "/orders", "rows[0].applianceId", "", "rows[0].number", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("order/newOrder"))
                .andExpect(model().attributeHasFieldErrors("order", "rows[0].applianceId", "rows[0].number"))
                .andExpect(content().string(containsString("is-invalid")))
                .andExpect(content().string(containsString("Please select an appliance")))
                .andExpect(content().string(containsString("Quantity is required")))
                .andExpect(content().string(containsString("Claw"))); // the appliance dropdown is filled again

        assertEquals(before, ordersRepository.count());
    }

    @Test
    void create_quantityOfZero_isRejected() throws Exception {
        mockMvc.perform(postAs(mercury, "/orders", "rows[0].applianceId", "1", "rows[0].number", "0"))
                .andExpect(model().attributeHasFieldErrors("order", "rows[0].number"))
                .andExpect(content().string(containsString("Quantity must be greater than zero")));
    }

    @Test
    void create_theSameApplianceTwice_isMergedIntoOneRow() throws Exception {
        MvcResult result = mockMvc.perform(postAs(mercury, "/orders",
                        "rows[0].applianceId", "1", "rows[0].number", "2",
                        "rows[1].applianceId", "1", "rows[1].number", "3"))
                .andReturn();

        Orders saved = reload(createdOrderId(result));
        assertEquals(1, saved.getOrderRowSet().size());
        OrderRow row = saved.getOrderRowSet().iterator().next();
        assertEquals(5L, row.getNumber());
        assertEquals(0, new BigDecimal("5.05").compareTo(row.getAmount()));
    }

    @Test
    void create_withSeveralAppliances_savesOneRowEach() throws Exception {
        MvcResult result = mockMvc.perform(postAs(mercury, "/orders",
                        "rows[0].applianceId", "1", "rows[0].number", "1",
                        "rows[1].applianceId", "2", "rows[1].number", "2"))
                .andReturn();

        Orders saved = reload(createdOrderId(result));
        assertEquals(2, saved.getOrderRowSet().size());
        BigDecimal total = saved.getOrderRowSet().stream().map(OrderRow::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("5.03").compareTo(total)); // 1.01 + 2 x 2.01
    }

    @Test
    void create_withAnUnknownAppliance_showsTheNotFoundPage() throws Exception {
        long before = ordersRepository.count();

        mockMvc.perform(postAs(mercury, "/orders", "rows[0].applianceId", "999999", "rows[0].number", "1"))
                .andExpect(status().isNotFound());

        assertEquals(before, ordersRepository.count());
    }

    @Test
    void create_withNoRowsAtAll_showsTheBadRequestPage() throws Exception {
        long before = ordersRepository.count();

        mockMvc.perform(postAs(mercury, "/orders"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/error"));

        assertEquals(before, ordersRepository.count());
    }

    @Test
    void create_priceApprovalAndEmployeeCannotBeChosenByTheClient() throws Exception {
        MvcResult result = mockMvc.perform(postAs(mercury, "/orders",
                        "rows[0].applianceId", "1", "rows[0].number", "1",
                        "rows[0].amount", "0.01", "approved", "true", "employeeId", "1", "id", "999999"))
                .andReturn();

        Orders saved = reload(createdOrderId(result));
        assertFalse(saved.getApproved());
        assertNull(saved.getEmployee());
        assertNotEquals(999999L, saved.getId());
        assertEquals(0, new BigDecimal("1.01").compareTo(saved.getOrderRowSet().iterator().next().getAmount()));
    }

    @Test
    void create_addItem_showsAnExtraRow_withoutSavingAnythingOrShowingErrors() throws Exception {
        long before = ordersRepository.count();

        mockMvc.perform(postAs(mercury, "/orders",
                        "rows[0].applianceId", "", "rows[0].number", "", "addRow", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("order/newOrder"))
                .andExpect(model().attribute("order", hasProperty("rows", hasSize(2))))
                .andExpect(model().attributeHasNoErrors("order"));

        assertEquals(before, ordersRepository.count());
    }

    @Test
    void create_removeItem_removesThatRow() throws Exception {
        mockMvc.perform(postAs(mercury, "/orders",
                        "rows[0].applianceId", "1", "rows[0].number", "1",
                        "rows[1].applianceId", "2", "rows[1].number", "3",
                        "removeRow", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("order", hasProperty("rows", hasSize(1))))
                .andExpect(model().attribute("order",
                        hasProperty("rows", hasItem(hasProperty("applianceId", is(2L))))))
                .andExpect(model().attributeHasNoErrors("order"));
    }

    @Test
    void create_removingTheLastItem_leavesOneBlankRow() throws Exception {
        mockMvc.perform(postAs(mercury, "/orders",
                        "rows[0].applianceId", "1", "rows[0].number", "4", "removeRow", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("order", hasProperty("rows", hasSize(1))))
                .andExpect(model().attribute("order",
                        hasProperty("rows", hasItem(hasProperty("applianceId", is((Object) null))))));
    }

    @Test
    void create_errorsAreShownInUkrainian() throws Exception {
        // the escapes spell the Ukrainian "Please select an appliance" (Choose the appliance)
        mockMvc.perform(postAs(mercury, "/orders",
                        "rows[0].applianceId", "", "rows[0].number", "1", "lang", "uk"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\u041e\u0431\u0435\u0440\u0456\u0442\u044c \u0442\u0435\u0445\u043d\u0456\u043a\u0443")));
    }

    @Test
    void create_withoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/orders").cookie(mercury)
                        .param("rows[0].applianceId", "1").param("rows[0].number", "1"))
                .andExpect(status().isForbidden());
    }

    // ---------- edit ----------

    @Test
    void editForm_showsTheExistingRows() throws Exception {
        Orders order = placeOrder(mercuryClient(), 1, 3);

        mockMvc.perform(get("/orders/" + order.getId() + "/edit").cookie(mercury))
                .andExpect(status().isOk())
                .andExpect(view().name("order/editOrder"))
                .andExpect(model().attribute("editing", true))
                .andExpect(content().string(containsString("Edit order")))
                .andExpect(content().string(containsString("value=\"3\"")))
                .andExpect(content().string(containsString("Mercury")));
    }

    @Test
    void editForm_ofAnotherClientsOrder_showsTheNotFoundPage() throws Exception {
        Orders ofVenus = placeOrder(venusClient(), 1, 1);

        mockMvc.perform(get("/orders/" + ofVenus.getId() + "/edit").cookie(mercury))
                .andExpect(status().isNotFound());
    }

    @Test
    void editForm_ofAnApprovedOrder_isRefused() throws Exception {
        Orders approved = placeApprovedOrder(mercuryClient(), 1, 1);

        mockMvc.perform(get("/orders/" + approved.getId() + "/edit").cookie(mercury))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("has already been approved")));
    }

    @Test
    void editForm_unknownOrder_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(get("/orders/999999/edit").cookie(employee))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_replacesTheRows() throws Exception {
        Orders order = placeOrder(mercuryClient(), 1, 3);

        mockMvc.perform(postAs(mercury, "/orders/" + order.getId(),
                        "rows[0].applianceId", "2", "rows[0].number", "1"))
                .andExpect(redirectedUrl("/orders/" + order.getId() + "?updated"));

        Orders changed = reload(order.getId());
        assertEquals(1, changed.getOrderRowSet().size());
        OrderRow row = changed.getOrderRowSet().iterator().next();
        assertEquals(2L, row.getAppliance().getId());
        assertEquals(1L, row.getNumber());
        assertEquals(0, new BigDecimal("2.01").compareTo(row.getAmount()));
    }

    @Test
    void update_byAnEmployee_ofAClientsOrder_works() throws Exception {
        Orders order = placeOrder(mercuryClient(), 1, 1);

        mockMvc.perform(postAs(employee, "/orders/" + order.getId(),
                        "rows[0].applianceId", "3", "rows[0].number", "2"))
                .andExpect(redirectedUrl("/orders/" + order.getId() + "?updated"));

        assertEquals(3L, reload(order.getId()).getOrderRowSet().iterator().next().getAppliance().getId());
    }

    @Test
    void update_cannotChangeWhoseOrderItIs() throws Exception {
        Orders order = placeOrder(mercuryClient(), 1, 1);

        mockMvc.perform(postAs(mercury, "/orders/" + order.getId(),
                        "clientId", String.valueOf(venusClient().getId()),
                        "rows[0].applianceId", "1", "rows[0].number", "1"))
                .andExpect(redirectedUrl("/orders/" + order.getId() + "?updated"));

        assertEquals("Mercury", reload(order.getId()).getClient().getName());
    }

    @Test
    void update_withErrors_showsTheEditFormAgain_andKeepsTheOldRows() throws Exception {
        Orders order = placeOrder(mercuryClient(), 1, 3);

        mockMvc.perform(postAs(mercury, "/orders/" + order.getId(),
                        "rows[0].applianceId", "", "rows[0].number", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("order/editOrder"))
                .andExpect(model().attributeHasFieldErrors("order", "rows[0].applianceId"))
                .andExpect(content().string(containsString("Edit order")));

        OrderRow row = reload(order.getId()).getOrderRowSet().iterator().next();
        assertEquals(1L, row.getAppliance().getId());
        assertEquals(3L, row.getNumber());
    }

    @Test
    void update_addItem_showsAnExtraRow_withoutSaving() throws Exception {
        Orders order = placeOrder(mercuryClient(), 1, 3);

        mockMvc.perform(postAs(mercury, "/orders/" + order.getId(),
                        "rows[0].applianceId", "1", "rows[0].number", "3", "addRow", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("order/editOrder"))
                .andExpect(model().attribute("order", hasProperty("rows", hasSize(2))));

        assertEquals(1, reload(order.getId()).getOrderRowSet().size());
    }

    @Test
    void update_ofAnApprovedOrder_isRefused_andNothingChanges() throws Exception {
        Orders approved = placeApprovedOrder(mercuryClient(), 1, 3);

        mockMvc.perform(postAs(mercury, "/orders/" + approved.getId(),
                        "rows[0].applianceId", "2", "rows[0].number", "1"))
                .andExpect(status().isConflict());

        assertEquals(1L, reload(approved.getId()).getOrderRowSet().iterator().next().getAppliance().getId());
    }

    @Test
    void update_ofAnotherClientsOrder_showsTheNotFoundPage_andNothingChanges() throws Exception {
        Orders ofVenus = placeOrder(venusClient(), 1, 3);

        mockMvc.perform(postAs(mercury, "/orders/" + ofVenus.getId(),
                        "rows[0].applianceId", "2", "rows[0].number", "1"))
                .andExpect(status().isNotFound());

        assertEquals(1L, reload(ofVenus.getId()).getOrderRowSet().iterator().next().getAppliance().getId());
    }

    @Test
    void update_unknownOrder_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(postAs(employee, "/orders/999999", "rows[0].applianceId", "1", "rows[0].number", "1"))
                .andExpect(status().isNotFound());
    }

    // ---------- approve ----------

    @Test
    void approve_byAnEmployee_marksTheOrderApprovedAndRecordsWho() throws Exception {
        Orders order = placeOrder(mercuryClient(), 1, 1);

        mockMvc.perform(postAs(employee, "/orders/" + order.getId() + "/approve"))
                .andExpect(redirectedUrl("/orders/" + order.getId() + "?approved"));

        Orders approved = reload(order.getId());
        assertTrue(approved.getApproved());
        assertEquals("Phobos", approved.getEmployee().getName());
    }

    @Test
    void approve_byAClient_isForbidden_andTheOrderStaysPending() throws Exception {
        Orders order = placeOrder(mercuryClient(), 1, 1);

        mockMvc.perform(postAs(mercury, "/orders/" + order.getId() + "/approve"))
                .andExpect(status().isForbidden());

        assertFalse(reload(order.getId()).getApproved());
    }

    @Test
    void approve_anAlreadyApprovedOrder_redirectsWithAMessage() throws Exception {
        Orders approved = placeApprovedOrder(mercuryClient(), 1, 1);

        mockMvc.perform(postAs(employee, "/orders/" + approved.getId() + "/approve"))
                .andExpect(redirectedUrl("/orders/" + approved.getId() + "?alreadyApproved"));
    }

    @Test
    void approve_unknownOrder_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(postAs(employee, "/orders/999999/approve"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approve_withoutCsrfToken_isForbidden() throws Exception {
        Orders order = placeOrder(mercuryClient(), 1, 1);

        mockMvc.perform(MockMvcRequestBuilders.post("/orders/" + order.getId() + "/approve").cookie(employee))
                .andExpect(status().isForbidden());

        assertFalse(reload(order.getId()).getApproved());
    }

    // ---------- delete ----------

    @Test
    void delete_byTheClient_removesTheirPendingOrder() throws Exception {
        Orders order = placeOrder(mercuryClient(), 1, 1);

        mockMvc.perform(postAs(mercury, "/orders/" + order.getId() + "/delete"))
                .andExpect(redirectedUrl("/orders?deleted"));

        assertFalse(ordersRepository.existsById(order.getId()));
    }

    @Test
    void delete_byAnEmployee_removesAnyPendingOrder() throws Exception {
        Orders order = placeOrder(venusClient(), 1, 1);

        mockMvc.perform(postAs(employee, "/orders/" + order.getId() + "/delete"))
                .andExpect(redirectedUrl("/orders?deleted"));

        assertFalse(ordersRepository.existsById(order.getId()));
    }

    @Test
    void delete_anotherClientsOrder_showsTheNotFoundPage_andKeepsTheOrder() throws Exception {
        Orders ofVenus = placeOrder(venusClient(), 1, 1);

        mockMvc.perform(postAs(mercury, "/orders/" + ofVenus.getId() + "/delete"))
                .andExpect(status().isNotFound());

        assertTrue(ordersRepository.existsById(ofVenus.getId()));
    }

    @Test
    void delete_anApprovedOrder_isRefusedWithAMessage_andKept() throws Exception {
        Orders approved = placeApprovedOrder(mercuryClient(), 1, 1);

        mockMvc.perform(postAs(mercury, "/orders/" + approved.getId() + "/delete"))
                .andExpect(redirectedUrl("/orders/" + approved.getId() + "?alreadyApproved"));

        assertTrue(ordersRepository.existsById(approved.getId()));
    }

    @Test
    void delete_unknownOrder_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(postAs(employee, "/orders/999999/delete"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_withoutCsrfToken_isForbidden() throws Exception {
        Orders order = placeOrder(mercuryClient(), 1, 1);

        mockMvc.perform(MockMvcRequestBuilders.post("/orders/" + order.getId() + "/delete").cookie(mercury))
                .andExpect(status().isForbidden());

        assertTrue(ordersRepository.existsById(order.getId()));
    }

    // ---------- the whole journey ----------

    @Test
    void journey_aClientOrders_anEmployeeApproves_theClientSeesItApproved() throws Exception {
        MvcResult created = mockMvc.perform(postAs(mercury, "/orders",
                        "rows[0].applianceId", "1", "rows[0].number", "2"))
                .andReturn();
        long id = createdOrderId(created);

        mockMvc.perform(get("/orders/" + id).cookie(venusCookie()))
                .andExpect(status().isNotFound()); // another client cannot see it

        mockMvc.perform(postAs(employee, "/orders/" + id + "/approve"))
                .andExpect(redirectedUrl("/orders/" + id + "?approved"));

        MvcResult seenByClient = mockMvc.perform(get("/orders/" + id).cookie(mercury))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Approved")))
                .andExpect(content().string(containsString("Phobos")))
                .andReturn();
        assertFalse(html(seenByClient).contains("/orders/" + id + "/edit"));

        mockMvc.perform(postAs(mercury, "/orders/" + id + "/delete"))
                .andExpect(redirectedUrl("/orders/" + id + "?alreadyApproved"));
    }
}

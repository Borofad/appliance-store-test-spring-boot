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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
 * The Appliances pages end to end (real security, templates, validation messages, seeded H2 data).
 * Every test is rolled back. Seed data: 7 appliances; Claw (id 1) belongs to Samsung (id 1) and costs 1.01;
 * Gust belongs to Lenovo; Halo costs 7.01; none of them is in an order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApplianceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplianceRepository applianceRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private OrdersRepository ordersRepository;

    private Cookie employee;

    @BeforeEach
    void logIn() throws Exception {
        employee = loginAsEmployee(mockMvc);
    }

    private String html(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    /**
     * A POST to the given URL with a complete, valid form. Pairs of (field, value) given as overrides REPLACE
     * the default value of that field. (Adding a second value with .param() would not work: for numbers and
     * enums Spring uses the first value, so the "wrong" value would be silently ignored.)
     */
    private MockHttpServletRequestBuilder validForm(String url, String... overrides) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("name", "Test Fridge");
        form.put("category", "SMALL");
        form.put("model", "TF-1");
        form.put("manufacturerId", "1");
        form.put("powerType", "AC220");
        form.put("characteristic", "quiet");
        form.put("description", "A test fridge");
        form.put("power", "150");
        form.put("price", "99.90");
        for (int i = 0; i < overrides.length; i += 2) {
            form.put(overrides[i], overrides[i + 1]);
        }
        MockHttpServletRequestBuilder request = authenticatedPost(mockMvc, employee, url);
        form.forEach((field, value) -> request.param(field, value));
        return request;
    }

    // ---------- list ----------

    @Test
    void list_withoutLogin_redirectsToTheLoginPage() throws Exception {
        mockMvc.perform(get("/appliances"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void list_showsTheSeededAppliancesWithTranslatedEnumsAndManufacturers() throws Exception {
        mockMvc.perform(get("/appliances").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(view().name("appliance/appliances"))
                .andExpect(content().string(containsString("Claw")))
                .andExpect(content().string(containsString("Kang Dae")))
                .andExpect(content().string(containsString("Samsung")))
                .andExpect(content().string(containsString("AC 220 V")))
                .andExpect(content().string(containsString("Accumulator")))
                .andExpect(model().attribute("page", hasProperty("totalElements", is(7L))));
    }

    @Test
    void list_search_findsByApplianceName() throws Exception {
        mockMvc.perform(get("/appliances").param("query", "gUs").cookie(employee))
                .andExpect(content().string(containsString("Gust")))
                .andExpect(content().string(not(containsString("Claw"))));
    }

    @Test
    void list_search_findsByManufacturerName() throws Exception {
        mockMvc.perform(get("/appliances").param("query", "lenovo").cookie(employee))
                .andExpect(content().string(containsString("Gust")))
                .andExpect(content().string(not(containsString("Claw"))));
    }

    @Test
    void list_search_withNoMatch_showsTheNoDataMessage() throws Exception {
        mockMvc.perform(get("/appliances").param("query", "zzzz").cookie(employee))
                .andExpect(content().string(containsString("No data to display")));
    }

    @Test
    void list_pagination_splitsTheResultIntoPages() throws Exception {
        MvcResult first = mockMvc.perform(get("/appliances").param("size", "5").cookie(employee))
                .andExpect(model().attribute("page", hasProperty("totalPages", is(2))))
                .andReturn();
        assertTrue(html(first).contains("page=1"), "there must be a link to the second page");

        mockMvc.perform(get("/appliances").param("size", "5").param("page", "1").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(model().attribute("page", hasProperty("number", is(1))));
    }

    @Test
    void list_sortingByPrice_canBeReversed() throws Exception {
        MvcResult asc = mockMvc.perform(get("/appliances")
                .param("sortField", "price").param("sortDir", "asc").cookie(employee)).andReturn();
        MvcResult desc = mockMvc.perform(get("/appliances")
                .param("sortField", "price").param("sortDir", "desc").cookie(employee)).andReturn();

        assertTrue(html(asc).indexOf("Claw") < html(asc).indexOf("Halo"));    // 1.01 before 7.01
        assertTrue(html(desc).indexOf("Halo") < html(desc).indexOf("Claw"));
    }

    @Test
    void list_sortingByManufacturerName_works() throws Exception {
        // HP (Bane) comes before Samsung (Claw) in any collation
        MvcResult asc = mockMvc.perform(get("/appliances")
                        .param("sortField", "manufacturer.name").param("sortDir", "asc").cookie(employee))
                .andExpect(status().isOk()).andReturn();
        MvcResult desc = mockMvc.perform(get("/appliances")
                        .param("sortField", "manufacturer.name").param("sortDir", "desc").cookie(employee))
                .andExpect(status().isOk()).andReturn();

        assertTrue(html(asc).indexOf("Bane") < html(asc).indexOf("Claw"));
        assertTrue(html(desc).indexOf("Claw") < html(desc).indexOf("Bane"));
    }

    @Test
    void list_hostileSortAndSizeValues_doNotBreakThePage() throws Exception {
        mockMvc.perform(get("/appliances")
                        .param("sortField", "manufacturer.password").param("sortDir", "sideways")
                        .param("size", "999999").param("page", "-4").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Claw")));
    }

    // ---------- create ----------

    @Test
    void newForm_showsTheDropdownsWithTranslatedOptions() throws Exception {
        mockMvc.perform(get("/appliances/new").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(view().name("appliance/newAppliance"))
                .andExpect(content().string(containsString("Add appliance")))
                .andExpect(content().string(containsString("Samsung")))
                .andExpect(content().string(containsString("Big")))
                .andExpect(content().string(containsString("AC 110 V")))
                .andExpect(content().string(containsString("-- Choose --")));
    }

    @Test
    void create_valid_savesTheApplianceWithItsManufacturer() throws Exception {
        mockMvc.perform(validForm("/appliances"))
                .andExpect(redirectedUrl("/appliances?created"));

        Appliance saved = applianceRepository.search("Test Fridge", PageRequest.of(0, 10)).getContent().get(0);
        assertEquals("Samsung", saved.getManufacturer().getName());
        assertEquals("TF-1", saved.getModel());
        assertEquals(150, saved.getPower());
        assertEquals(0, new BigDecimal("99.90").compareTo(saved.getPrice()));
    }

    @Test
    void create_emptyForm_showsAnErrorOnEveryRequiredField() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/appliances"))
                .andExpect(status().isOk())
                .andExpect(view().name("appliance/newAppliance"))
                .andExpect(model().attributeHasFieldErrors("appliance",
                        "name", "category", "model", "manufacturerId", "powerType", "power", "price"))
                .andExpect(content().string(containsString("is-invalid")))
                .andExpect(content().string(containsString("Please select a manufacturer")))
                .andExpect(content().string(containsString("Price is required")))
                // the dropdowns must still be filled after a failed submit
                .andExpect(content().string(containsString("Samsung")));
    }

    @Test
    void create_priceThatIsNotANumber_showsTheTypeMismatchMessage() throws Exception {
        mockMvc.perform(validForm("/appliances", "price", "abc"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("appliance", "price"))
                .andExpect(content().string(containsString("Please enter a valid number")));
    }

    @Test
    void create_priceBelowTheMinimum_isRejected() throws Exception {
        mockMvc.perform(validForm("/appliances", "price", "0"))
                .andExpect(model().attributeHasFieldErrors("appliance", "price"))
                .andExpect(content().string(containsString("Price must be at least 0.01")));
    }

    @Test
    void create_priceWithTooManyDecimals_isRejected() throws Exception {
        mockMvc.perform(validForm("/appliances", "price", "1.234"))
                .andExpect(model().attributeHasFieldErrors("appliance", "price"))
                .andExpect(content().string(containsString("2 decimal places")));
    }

    @Test
    void create_negativePower_isRejected() throws Exception {
        mockMvc.perform(validForm("/appliances", "power", "-5"))
                .andExpect(model().attributeHasFieldErrors("appliance", "power"))
                .andExpect(content().string(containsString("Power must be a positive number")));
    }

    @Test
    void create_unknownCategory_isRejected() throws Exception {
        mockMvc.perform(validForm("/appliances", "category", "HUGE"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("appliance", "category"));
    }

    @Test
    void create_tooLongName_showsTheSizeError() throws Exception {
        mockMvc.perform(validForm("/appliances", "name", "x".repeat(101)))
                .andExpect(model().attributeHasFieldErrors("appliance", "name"))
                .andExpect(content().string(containsString("must not exceed 100 characters")));
    }

    @Test
    void create_errorsAreShownInUkrainian() throws Exception {
        // "\u041e\u0431\u0435\u0440\u0456\u0442\u044c" = "Choose" (the Ukrainian "Please select" message starts with it)
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/appliances").param("lang", "uk"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\u041e\u0431\u0435\u0440\u0456\u0442\u044c")));
    }

    @Test
    void create_unknownManufacturer_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(validForm("/appliances", "manufacturerId", "9999"))
                .andExpect(status().isNotFound());
        assertTrue(applianceRepository.search("Test Fridge", PageRequest.of(0, 10)).isEmpty());
    }

    @Test
    void create_ignoresAnIdSentInTheForm() throws Exception {
        mockMvc.perform(validForm("/appliances", "id", "1"))
                .andExpect(redirectedUrl("/appliances?created"));

        assertEquals("Claw", applianceRepository.findById(1L).orElseThrow().getName());
    }

    @Test
    void create_withoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/appliances").cookie(employee).param("name", "x"))
                .andExpect(status().isForbidden());
    }

    // ---------- edit ----------

    @Test
    void editForm_showsTheCurrentValues() throws Exception {
        mockMvc.perform(get("/appliances/1/edit").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(view().name("appliance/newAppliance"))
                .andExpect(content().string(containsString("Edit appliance")))
                .andExpect(content().string(containsString("value=\"Claw\"")))
                .andExpect(content().string(containsString("value=\"600\"")));
    }

    @Test
    void editForm_unknownId_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(get("/appliances/999/edit").cookie(employee))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/error"));
    }

    @Test
    void update_valid_changesTheAppliance() throws Exception {
        mockMvc.perform(validForm("/appliances/1", "manufacturerId", "2"))
                .andExpect(redirectedUrl("/appliances?updated"));

        Appliance changed = applianceRepository.findById(1L).orElseThrow();
        assertEquals("Test Fridge", changed.getName());
        assertEquals("Dell", changed.getManufacturer().getName());
        assertEquals(0, new BigDecimal("99.90").compareTo(changed.getPrice()));
    }

    @Test
    void update_withErrors_showsTheEditFormAndKeepsTheOldData() throws Exception {
        mockMvc.perform(validForm("/appliances/1", "price", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("appliance/newAppliance"))
                .andExpect(model().attributeHasFieldErrors("appliance", "price"))
                .andExpect(content().string(containsString("Edit appliance")));

        assertEquals("Claw", applianceRepository.findById(1L).orElseThrow().getName());
    }

    @Test
    void update_unknownId_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(validForm("/appliances/999"))
                .andExpect(status().isNotFound());
    }

    // ---------- delete ----------

    @Test
    void delete_applianceThatIsNotInAnOrder_isRemoved() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/appliances/1/delete"))
                .andExpect(redirectedUrl("/appliances?deleted"));

        assertFalse(applianceRepository.existsById(1L));
    }

    @Test
    void delete_applianceInAnOrder_isKeptAndTheFailureIsReported() throws Exception {
        Client client = clientRepository.findByEmail("mercury@gmail.com").orElseThrow();
        Appliance claw = applianceRepository.findById(1L).orElseThrow();
        OrderRow row = new OrderRow();
        row.setAppliance(claw);
        row.setNumber(1L);
        row.setAmount(claw.getPrice());
        Orders order = new Orders();
        order.setClient(client);
        order.getOrderRowSet().add(row);
        ordersRepository.saveAndFlush(order);

        mockMvc.perform(authenticatedPost(mockMvc, employee, "/appliances/1/delete"))
                .andExpect(redirectedUrl("/appliances?deleteFailed"));

        assertTrue(applianceRepository.existsById(1L));
    }

    @Test
    void list_showsTheDeleteFailedMessage() throws Exception {
        mockMvc.perform(get("/appliances").param("deleteFailed", "").cookie(employee))
                .andExpect(content().string(containsString("cannot be deleted")));
    }

    @Test
    void delete_unknownId_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/appliances/999/delete"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_withoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/appliances/1/delete").cookie(employee))
                .andExpect(status().isForbidden());
    }
}

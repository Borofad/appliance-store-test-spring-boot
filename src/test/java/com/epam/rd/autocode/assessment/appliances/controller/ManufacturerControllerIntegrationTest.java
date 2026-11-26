package com.epam.rd.autocode.assessment.appliances.controller;

import com.epam.rd.autocode.assessment.appliances.repository.ManufacturerRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static com.epam.rd.autocode.assessment.appliances.support.AuthTestSupport.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The Manufacturers pages end to end: real security (login cookie, CSRF), real templates, real validation
 * messages and the seeded H2 data. Every test is rolled back, so tests cannot affect each other.
 * Seed data: 7 manufacturers, Samsung has id 1 and is used by an appliance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ManufacturerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ManufacturerRepository manufacturerRepository;

    private Cookie employee;

    @BeforeEach
    void logIn() throws Exception {
        employee = loginAsEmployee(mockMvc);
    }

    private String html(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    // ---------- list ----------

    @Test
    void list_withoutLogin_redirectsToTheLoginPage() throws Exception {
        mockMvc.perform(get("/manufacturers"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void list_showsTheSeededManufacturers() throws Exception {
        mockMvc.perform(get("/manufacturers").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(view().name("manufacture/manufacturers"))
                .andExpect(content().string(containsString("Samsung")))
                .andExpect(content().string(containsString("Lenovo")))
                .andExpect(model().attribute("page", hasProperty("totalElements", is(7L))));
    }

    @Test
    void list_search_filtersByName_ignoringCase() throws Exception {
        mockMvc.perform(get("/manufacturers").param("query", "sAm").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Samsung")))
                .andExpect(content().string(not(containsString("Lenovo"))));
    }

    @Test
    void list_search_withNoMatch_showsTheNoDataMessage() throws Exception {
        mockMvc.perform(get("/manufacturers").param("query", "zzzz").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No data to display")));
    }

    @Test
    void list_pagination_splitsTheResultIntoPages() throws Exception {
        MvcResult first = mockMvc.perform(get("/manufacturers").param("size", "5").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(model().attribute("page", hasProperty("totalPages", is(2))))
                .andReturn();
        assertFalse(html(first).contains("Samsung"), "Samsung is on the second page");
        assertTrue(html(first).contains("page=1"), "there must be a link to the second page");

        mockMvc.perform(get("/manufacturers").param("size", "5").param("page", "1").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Samsung")))
                .andExpect(content().string(containsString("Lenovo")));
    }

    @Test
    void list_sorting_canBeReversed() throws Exception {
        MvcResult asc = mockMvc.perform(get("/manufacturers")
                .param("sortField", "name").param("sortDir", "asc").cookie(employee)).andReturn();
        MvcResult desc = mockMvc.perform(get("/manufacturers")
                .param("sortField", "name").param("sortDir", "desc").cookie(employee)).andReturn();

        assertTrue(html(asc).indexOf("Samsung") > html(asc).indexOf("Acer"));
        assertTrue(html(desc).indexOf("Samsung") < html(desc).indexOf("Acer"));
    }

    @Test
    void list_hostileSortAndSizeValues_doNotBreakThePage() throws Exception {
        mockMvc.perform(get("/manufacturers")
                        .param("sortField", "password").param("sortDir", "sideways")
                        .param("size", "999999").param("page", "-4").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Samsung")));
    }

    @Test
    void list_nonNumericPage_showsTheBadRequestPage() throws Exception {
        mockMvc.perform(get("/manufacturers").param("page", "abc").cookie(employee))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/error"));
    }

    @Test
    void list_showsTheResultFlagsAsMessages() throws Exception {
        mockMvc.perform(get("/manufacturers").param("created", "").cookie(employee))
                .andExpect(content().string(containsString("Created successfully")));
        mockMvc.perform(get("/manufacturers").param("deleteFailed", "").cookie(employee))
                .andExpect(content().string(containsString("cannot be deleted")));
    }

    // ---------- create ----------

    @Test
    void newForm_isShown() throws Exception {
        mockMvc.perform(get("/manufacturers/new").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(view().name("manufacture/newManufacturer"))
                .andExpect(content().string(containsString("Add manufacturer")));
    }

    @Test
    void create_valid_savesTheManufacturer() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/manufacturers").param("name", "ZZ-Temp"))
                .andExpect(redirectedUrl("/manufacturers?created"));

        assertTrue(manufacturerRepository.findByName("ZZ-Temp").isPresent());
        mockMvc.perform(get("/manufacturers").param("query", "zz-temp").cookie(employee))
                .andExpect(content().string(containsString("ZZ-Temp")));
    }

    @Test
    void create_blankName_showsTheErrorOnTheField() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/manufacturers").param("name", "  "))
                .andExpect(status().isOk())
                .andExpect(view().name("manufacture/newManufacturer"))
                .andExpect(model().attributeHasFieldErrors("manufacturer", "name"))
                .andExpect(content().string(containsString("is-invalid")))
                .andExpect(content().string(containsString("Manufacturer name is required")));
    }

    @Test
    void create_blankName_showsTheErrorInUkrainian() throws Exception {
        // "\u041d\u0430\u0437\u0432\u0430 \u0432\u0438\u0440\u043e\u0431\u043d\u0438\u043a\u0430" = "Name of the manufacturer"
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/manufacturers")
                        .param("name", "").param("lang", "uk"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "\u041d\u0430\u0437\u0432\u0430 \u0432\u0438\u0440\u043e\u0431\u043d\u0438\u043a\u0430")));
    }

    @Test
    void create_tooLongName_showsTheSizeError() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/manufacturers").param("name", "x".repeat(101)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("must not exceed 100 characters")));
    }

    @Test
    void create_duplicateName_isRejectedIgnoringCase() throws Exception {
        long before = manufacturerRepository.count();

        mockMvc.perform(authenticatedPost(mockMvc, employee, "/manufacturers").param("name", "samsung"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("manufacturer", "name"))
                .andExpect(content().string(containsString("already exists")));

        assertEquals(before, manufacturerRepository.count());
    }

    @Test
    void create_ignoresAnIdSentInTheForm() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/manufacturers")
                        .param("name", "ZZ-Temp").param("id", "1"))
                .andExpect(redirectedUrl("/manufacturers?created"));

        assertEquals("Samsung", manufacturerRepository.findById(1L).orElseThrow().getName());
        assertTrue(manufacturerRepository.findByName("ZZ-Temp").isPresent());
    }

    @Test
    void create_withoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/manufacturers").cookie(employee).param("name", "ZZ-Temp"))
                .andExpect(status().isForbidden());
    }

    // ---------- edit ----------

    @Test
    void editForm_showsTheCurrentName() throws Exception {
        mockMvc.perform(get("/manufacturers/1/edit").cookie(employee))
                .andExpect(status().isOk())
                .andExpect(view().name("manufacture/newManufacturer"))
                .andExpect(content().string(containsString("value=\"Samsung\"")))
                .andExpect(content().string(containsString("Edit manufacturer")));
    }

    @Test
    void editForm_unknownId_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(get("/manufacturers/999/edit").cookie(employee))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/error"));
    }

    @Test
    void update_valid_changesTheName() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/manufacturers/1").param("name", "Samsung Electronics"))
                .andExpect(redirectedUrl("/manufacturers?updated"));

        assertEquals("Samsung Electronics", manufacturerRepository.findById(1L).orElseThrow().getName());
    }

    @Test
    void update_keepingTheSameName_isNotADuplicateOfItself() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/manufacturers/1").param("name", "Samsung"))
                .andExpect(redirectedUrl("/manufacturers?updated"));
    }

    @Test
    void update_toAnotherManufacturersName_isRejected() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/manufacturers/1").param("name", "Dell"))
                .andExpect(status().isOk())
                .andExpect(view().name("manufacture/newManufacturer"))
                .andExpect(model().attributeHasFieldErrors("manufacturer", "name"))
                .andExpect(content().string(containsString("already exists")))
                .andExpect(content().string(containsString("Edit manufacturer")));

        assertEquals("Samsung", manufacturerRepository.findById(1L).orElseThrow().getName());
    }

    @Test
    void update_blankName_showsTheEditFormWithAnError() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/manufacturers/1").param("name", ""))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("manufacturer", "name"))
                .andExpect(content().string(containsString("Edit manufacturer")));
    }

    @Test
    void update_unknownId_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/manufacturers/999").param("name", "Brand New"))
                .andExpect(status().isNotFound());
    }

    // ---------- delete ----------

    @Test
    void delete_unusedManufacturer_isRemoved() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/manufacturers").param("name", "ZZ-Temp"));
        Long id = manufacturerRepository.findByName("ZZ-Temp").orElseThrow().getId();

        mockMvc.perform(authenticatedPost(mockMvc, employee, "/manufacturers/" + id + "/delete"))
                .andExpect(redirectedUrl("/manufacturers?deleted"));

        assertFalse(manufacturerRepository.existsById(id));
    }

    @Test
    void delete_manufacturerUsedByAppliances_isKeptAndTheFailureIsReported() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/manufacturers/1/delete"))
                .andExpect(redirectedUrl("/manufacturers?deleteFailed"));

        assertTrue(manufacturerRepository.existsById(1L));
    }

    @Test
    void delete_unknownId_showsTheNotFoundPage() throws Exception {
        mockMvc.perform(authenticatedPost(mockMvc, employee, "/manufacturers/999/delete"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_withoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/manufacturers/1/delete").cookie(employee))
                .andExpect(status().isForbidden());
    }
}

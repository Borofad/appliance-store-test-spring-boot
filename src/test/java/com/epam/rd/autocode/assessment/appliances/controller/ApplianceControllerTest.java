package com.epam.rd.autocode.assessment.appliances.controller;

import com.epam.rd.autocode.assessment.appliances.dto.ApplianceDto;
import com.epam.rd.autocode.assessment.appliances.dto.ManufacturerDto;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceNotFoundException;
import com.epam.rd.autocode.assessment.appliances.model.Category;
import com.epam.rd.autocode.assessment.appliances.model.PowerType;
import com.epam.rd.autocode.assessment.appliances.service.ApplianceService;
import com.epam.rd.autocode.assessment.appliances.service.ManufacturerService;
import com.epam.rd.autocode.assessment.appliances.util.PagingSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplianceControllerTest {

    @Mock
    private ApplianceService applianceService;
    @Mock
    private ManufacturerService manufacturerService;

    private ApplianceController controller;
    private Model model;

    @BeforeEach
    void setUp() {
        controller = new ApplianceController(applianceService, manufacturerService);
        model = new ConcurrentModel();
    }

    private ApplianceDto appliance(Long id, String name) {
        ApplianceDto dto = new ApplianceDto();
        dto.setId(id);
        dto.setName(name);
        dto.setCategory(Category.BIG);
        dto.setModel("M-1");
        dto.setManufacturerId(5L);
        dto.setPowerType(PowerType.AC220);
        dto.setPower(1500);
        dto.setPrice(new BigDecimal("99.90"));
        return dto;
    }

    private BindingResult cleanResult(ApplianceDto dto) {
        return new BeanPropertyBindingResult(dto, "appliance");
    }

    private BindingResult resultWithPriceError(ApplianceDto dto) {
        BindingResult result = cleanResult(dto);
        result.rejectValue("price", "validation.appliance.price.required");
        return result;
    }

    private void stubDropdowns() {
        when(manufacturerService.findAll()).thenReturn(List.of(new ManufacturerDto(5L, "Samsung")));
    }

    private void assertDropdownsAreInTheModel(boolean editing) {
        assertEquals(editing, model.getAttribute("editing"));
        assertEquals(1, ((List<?>) model.getAttribute("manufacturers")).size());
        assertArrayEquals(Category.values(), (Category[]) model.getAttribute("categories"));
        assertArrayEquals(PowerType.values(), (PowerType[]) model.getAttribute("powerTypes"));
    }

    // ---------- list ----------

    @Test
    void list_searchesWithTheRequestedPageSizeAndSort_andFillsTheModel() {
        when(applianceService.search(eq("claw"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(appliance(1L, "Claw"))));

        String view = controller.list("claw", 1, 20, "price", "desc", model);

        assertEquals("appliance/appliances", view);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(applianceService).search(eq("claw"), captor.capture());
        assertEquals(1, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("price").getDirection());

        assertNotNull(model.getAttribute("page"));
        assertEquals("claw", model.getAttribute("query"));
        assertEquals(20, model.getAttribute("size"));
        assertEquals("price", model.getAttribute("sortField"));
        assertEquals("desc", model.getAttribute("sortDir"));
        assertEquals(PagingSupport.PAGE_SIZES, model.getAttribute("pageSizes"));
        verifyNoInteractions(manufacturerService); // the list page needs no dropdown data
    }

    @Test
    void list_canSortByTheManufacturersName() {
        when(applianceService.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.list("", 0, 10, "manufacturer.name", "asc", model);

        assertEquals("manufacturer.name", model.getAttribute("sortField"));
    }

    @Test
    void list_ignoresHostileSortAndSizeValues() {
        when(applianceService.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.list("", -3, 7, "manufacturer.password", "sideways", model);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(applianceService).search(any(), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(PagingSupport.DEFAULT_SIZE, captor.getValue().getPageSize());
        assertEquals("name", model.getAttribute("sortField"));
        assertEquals("asc", model.getAttribute("sortDir"));
    }

    @Test
    void list_cutsAVeryLongSearchText() {
        when(applianceService.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.list("x".repeat(500), 0, 10, "name", "asc", model);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(applianceService).search(captor.capture(), any(Pageable.class));
        assertEquals(100, captor.getValue().length());
    }

    // ---------- create ----------

    @Test
    void newForm_showsAnEmptyFormWithTheDropdownContents() {
        stubDropdowns();

        assertEquals("appliance/newAppliance", controller.newForm(model));
        assertDropdownsAreInTheModel(false);
    }

    @Test
    void create_valid_savesAndRedirectsWithTheCreatedFlag() {
        ApplianceDto dto = appliance(null, "Fridge");

        String view = controller.create(dto, cleanResult(dto), model);

        assertEquals("redirect:/appliances?created", view);
        verify(applianceService).create(dto);
        verifyNoInteractions(manufacturerService);
    }

    @Test
    void create_withErrors_showsTheFormAgainWithDropdownsAndSavesNothing() {
        stubDropdowns();
        ApplianceDto dto = appliance(null, "Fridge");

        String view = controller.create(dto, resultWithPriceError(dto), model);

        assertEquals("appliance/newAppliance", view);
        assertDropdownsAreInTheModel(false);
        verify(applianceService, never()).create(any());
    }

    // ---------- edit ----------

    @Test
    void editForm_loadsTheApplianceAndTheDropdownContents() {
        stubDropdowns();
        when(applianceService.findById(5L)).thenReturn(appliance(5L, "Fridge"));

        String view = controller.editForm(5L, model);

        assertEquals("appliance/newAppliance", view);
        assertEquals("Fridge", ((ApplianceDto) model.getAttribute("appliance")).getName());
        assertDropdownsAreInTheModel(true);
    }

    @Test
    void editForm_unknownId_propagatesNotFound() {
        when(applianceService.findById(9L)).thenThrow(ResourceNotFoundException.appliance(9L));

        assertThrows(ResourceNotFoundException.class, () -> controller.editForm(9L, model));
    }

    @Test
    void update_valid_savesAndRedirectsWithTheUpdatedFlag() {
        ApplianceDto dto = appliance(5L, "Fridge");

        String view = controller.update(5L, dto, cleanResult(dto), model);

        assertEquals("redirect:/appliances?updated", view);
        verify(applianceService).update(5L, dto);
    }

    @Test
    void update_withErrors_showsTheEditFormAgainAndSavesNothing() {
        stubDropdowns();
        ApplianceDto dto = appliance(5L, "Fridge");

        String view = controller.update(5L, dto, resultWithPriceError(dto), model);

        assertEquals("appliance/newAppliance", view);
        assertDropdownsAreInTheModel(true);
        verify(applianceService, never()).update(any(), any());
    }

    // ---------- delete ----------

    @Test
    void delete_redirectsWithTheDeletedFlag() {
        assertEquals("redirect:/appliances?deleted", controller.delete(5L));

        verify(applianceService).delete(5L);
    }

    @Test
    void delete_applianceInAnOrder_redirectsWithTheFailedFlag() {
        doThrow(ResourceInUseException.appliance()).when(applianceService).delete(1L);

        assertEquals("redirect:/appliances?deleteFailed", controller.delete(1L));
    }

    @Test
    void delete_unknownId_propagatesNotFound() {
        doThrow(ResourceNotFoundException.appliance(9L)).when(applianceService).delete(9L);

        assertThrows(ResourceNotFoundException.class, () -> controller.delete(9L));
    }

    // ---------- form object ----------

    @Test
    void applianceForm_takesTheIdFromTheUrl() {
        assertEquals(7L, controller.applianceForm(7L).getId());
    }

    @Test
    void applianceForm_withoutAnId_isANewAppliance() {
        assertNull(controller.applianceForm(null).getId());
    }
}

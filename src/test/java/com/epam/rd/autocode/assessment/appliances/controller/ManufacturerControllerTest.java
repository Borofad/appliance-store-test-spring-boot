package com.epam.rd.autocode.assessment.appliances.controller;

import com.epam.rd.autocode.assessment.appliances.dto.ManufacturerDto;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceNotFoundException;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManufacturerControllerTest {

    @Mock
    private ManufacturerService manufacturerService;

    private ManufacturerController controller;
    private Model model;

    @BeforeEach
    void setUp() {
        controller = new ManufacturerController(manufacturerService);
        model = new ConcurrentModel();
    }

    private Page<ManufacturerDto> pageOf(ManufacturerDto... items) {
        return new PageImpl<>(List.of(items));
    }

    private BindingResult cleanResult(ManufacturerDto dto) {
        return new BeanPropertyBindingResult(dto, "manufacturer");
    }

    private BindingResult resultWithNameError(ManufacturerDto dto) {
        BindingResult result = cleanResult(dto);
        result.rejectValue("name", "validation.manufacturer.name.required");
        return result;
    }

    // ---------- list ----------

    @Test
    void list_searchesWithTheRequestedPageSizeAndSort_andFillsTheModel() {
        when(manufacturerService.search(eq("son"), any(Pageable.class))).thenReturn(pageOf(new ManufacturerDto(1L, "Sony")));

        String view = controller.list("son", 2, 20, "name", "desc", model);

        assertEquals("manufacture/manufacturers", view);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(manufacturerService).search(eq("son"), captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("name").getDirection());

        assertNotNull(model.getAttribute("page"));
        assertEquals("son", model.getAttribute("query"));
        assertEquals(20, model.getAttribute("size"));
        assertEquals("name", model.getAttribute("sortField"));
        assertEquals("desc", model.getAttribute("sortDir"));
        assertEquals(PagingSupport.PAGE_SIZES, model.getAttribute("pageSizes"));
    }

    @Test
    void list_ignoresHostileSortAndSizeValues() {
        when(manufacturerService.search(eq(""), any(Pageable.class))).thenReturn(pageOf());

        controller.list("", -3, 7, "password", "sideways", model);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(manufacturerService).search(eq(""), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(PagingSupport.DEFAULT_SIZE, captor.getValue().getPageSize());
        assertNull(captor.getValue().getSort().getOrderFor("password"));
        assertEquals("name", model.getAttribute("sortField"));
        assertEquals("asc", model.getAttribute("sortDir"));
    }

    @Test
    void list_cutsAVeryLongSearchText() {
        when(manufacturerService.search(any(), any(Pageable.class))).thenReturn(pageOf());

        controller.list("x".repeat(500), 0, 10, "name", "asc", model);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(manufacturerService).search(captor.capture(), any(Pageable.class));
        assertEquals(100, captor.getValue().length());
    }

    // ---------- create ----------

    @Test
    void newForm_showsAnEmptyCreateForm() {
        assertEquals("manufacture/newManufacturer", controller.newForm(model));
        assertEquals(false, model.getAttribute("editing"));
    }

    @Test
    void create_valid_savesAndRedirectsWithTheCreatedFlag() {
        ManufacturerDto dto = new ManufacturerDto(null, "Sony");

        String view = controller.create(dto, cleanResult(dto), model);

        assertEquals("redirect:/manufacturers?created", view);
        verify(manufacturerService).create(dto);
    }

    @Test
    void create_withErrors_showsTheFormAgainAndSavesNothing() {
        ManufacturerDto dto = new ManufacturerDto(null, "");

        String view = controller.create(dto, resultWithNameError(dto), model);

        assertEquals("manufacture/newManufacturer", view);
        assertEquals(false, model.getAttribute("editing"));
        verifyNoInteractions(manufacturerService);
    }

    // ---------- edit ----------

    @Test
    void editForm_loadsTheManufacturer() {
        when(manufacturerService.findById(5L)).thenReturn(new ManufacturerDto(5L, "Sony"));

        String view = controller.editForm(5L, model);

        assertEquals("manufacture/newManufacturer", view);
        assertEquals(true, model.getAttribute("editing"));
        assertEquals("Sony", ((ManufacturerDto) model.getAttribute("manufacturer")).getName());
    }

    @Test
    void editForm_unknownId_propagatesNotFound() {
        when(manufacturerService.findById(9L)).thenThrow(ResourceNotFoundException.manufacturer(9L));

        assertThrows(ResourceNotFoundException.class, () -> controller.editForm(9L, model));
    }

    @Test
    void update_valid_savesAndRedirectsWithTheUpdatedFlag() {
        ManufacturerDto dto = new ManufacturerDto(5L, "Sony Corp");

        String view = controller.update(5L, dto, cleanResult(dto), model);

        assertEquals("redirect:/manufacturers?updated", view);
        verify(manufacturerService).update(5L, dto);
    }

    @Test
    void update_withErrors_showsTheEditFormAgainAndSavesNothing() {
        ManufacturerDto dto = new ManufacturerDto(5L, "");

        String view = controller.update(5L, dto, resultWithNameError(dto), model);

        assertEquals("manufacture/newManufacturer", view);
        assertEquals(true, model.getAttribute("editing"));
        verifyNoInteractions(manufacturerService);
    }

    // ---------- delete ----------

    @Test
    void delete_redirectsWithTheDeletedFlag() {
        assertEquals("redirect:/manufacturers?deleted", controller.delete(5L));

        verify(manufacturerService).delete(5L);
    }

    @Test
    void delete_manufacturerInUse_redirectsWithTheFailedFlag() {
        doThrow(ResourceInUseException.manufacturer()).when(manufacturerService).delete(1L);

        assertEquals("redirect:/manufacturers?deleteFailed", controller.delete(1L));
    }

    @Test
    void delete_unknownId_propagatesNotFound() {
        doThrow(ResourceNotFoundException.manufacturer(9L)).when(manufacturerService).delete(9L);

        assertThrows(ResourceNotFoundException.class, () -> controller.delete(9L));
    }

    // ---------- form object ----------

    @Test
    void manufacturerForm_takesTheIdFromTheUrl() {
        assertEquals(7L, controller.manufacturerForm(7L).getId());
    }

    @Test
    void manufacturerForm_withoutAnId_isANewManufacturer() {
        assertNull(controller.manufacturerForm(null).getId());
    }
}

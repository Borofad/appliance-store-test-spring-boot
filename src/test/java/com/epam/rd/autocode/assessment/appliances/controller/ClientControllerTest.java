package com.epam.rd.autocode.assessment.appliances.controller;

import com.epam.rd.autocode.assessment.appliances.dto.ClientDto;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceNotFoundException;
import com.epam.rd.autocode.assessment.appliances.service.ClientService;
import com.epam.rd.autocode.assessment.appliances.util.PagingSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class ClientControllerTest {

    @Mock
    private ClientService clientService;

    private ClientController controller;
    private Model model;

    @BeforeEach
    void setUp() {
        controller = new ClientController(clientService);
        model = new ConcurrentModel();
    }

    private ClientDto client(Long id, String password) {
        ClientDto dto = new ClientDto();
        dto.setId(id);
        dto.setName("Anna");
        dto.setEmail("anna@x.com");
        dto.setCard("5326-0001");
        dto.setPassword(password);
        dto.setConfirmPassword(password);
        return dto;
    }

    private BindingResult cleanResult(ClientDto dto) {
        return new BeanPropertyBindingResult(dto, "client");
    }

    private BindingResult resultWithEmailError(ClientDto dto) {
        BindingResult result = cleanResult(dto);
        result.rejectValue("email", "validation.user.email.unique");
        return result;
    }

    // ---------- list ----------

    @Test
    void list_searchesWithTheRequestedPageSizeAndSort_andFillsTheModel() {
        when(clientService.search(eq("ann"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(client(1L, null))));

        String view = controller.list("ann", 3, 5, "email", "desc", model);

        assertEquals("client/clients", view);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(clientService).search(eq("ann"), captor.capture());
        assertEquals(3, captor.getValue().getPageNumber());
        assertEquals(5, captor.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("email").getDirection());

        assertNotNull(model.getAttribute("page"));
        assertEquals("ann", model.getAttribute("query"));
        assertEquals(5, model.getAttribute("size"));
        assertEquals("email", model.getAttribute("sortField"));
        assertEquals("desc", model.getAttribute("sortDir"));
        assertEquals(PagingSupport.PAGE_SIZES, model.getAttribute("pageSizes"));
    }

    @Test
    void list_doesNotAllowSortingByThePassword() {
        when(clientService.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.list("", 0, 10, "password", "asc", model);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(clientService).search(any(), captor.capture());
        assertNull(captor.getValue().getSort().getOrderFor("password"));
        assertEquals("name", model.getAttribute("sortField"));
    }

    @Test
    void list_ignoresHostileSizeAndPageValues() {
        when(clientService.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.list("", -3, 7, "name", "sideways", model);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(clientService).search(any(), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(PagingSupport.DEFAULT_SIZE, captor.getValue().getPageSize());
        assertEquals("asc", model.getAttribute("sortDir"));
    }

    @Test
    void list_cutsAVeryLongSearchText() {
        when(clientService.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.list("x".repeat(500), 0, 10, "name", "asc", model);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(clientService).search(captor.capture(), any(Pageable.class));
        assertEquals(100, captor.getValue().length());
    }

    // ---------- create ----------

    @Test
    void newForm_showsAnEmptyCreateForm() {
        assertEquals("client/newClient", controller.newForm(model));
        assertEquals(false, model.getAttribute("editing"));
    }

    @Test
    void create_valid_savesAndRedirectsWithTheCreatedFlag() {
        ClientDto dto = client(null, "Str0ng!Pass");

        String view = controller.create(dto, cleanResult(dto), model);

        assertEquals("redirect:/clients?created", view);
        verify(clientService).create(dto);
    }

    @Test
    void create_withErrors_showsTheFormAgain_withoutSendingThePasswordsBack() {
        ClientDto dto = client(null, "Str0ng!Pass");

        String view = controller.create(dto, resultWithEmailError(dto), model);

        assertEquals("client/newClient", view);
        assertEquals(false, model.getAttribute("editing"));
        assertNull(dto.getPassword());
        assertNull(dto.getConfirmPassword());
        verifyNoInteractions(clientService);
    }

    // ---------- edit ----------

    @Test
    void editForm_loadsTheClient() {
        when(clientService.findById(5L)).thenReturn(client(5L, null));

        String view = controller.editForm(5L, model);

        assertEquals("client/newClient", view);
        assertEquals(true, model.getAttribute("editing"));
        assertEquals("Anna", ((ClientDto) model.getAttribute("client")).getName());
    }

    @Test
    void editForm_unknownId_propagatesNotFound() {
        when(clientService.findById(9L)).thenThrow(ResourceNotFoundException.client(9L));

        assertThrows(ResourceNotFoundException.class, () -> controller.editForm(9L, model));
    }

    @Test
    void update_valid_savesAndRedirectsWithTheUpdatedFlag() {
        ClientDto dto = client(5L, "");

        String view = controller.update(5L, dto, cleanResult(dto), model);

        assertEquals("redirect:/clients?updated", view);
        verify(clientService).update(5L, dto);
    }

    @Test
    void update_withErrors_showsTheEditFormAgain_withoutSendingThePasswordsBack() {
        ClientDto dto = client(5L, "N3w!Password");

        String view = controller.update(5L, dto, resultWithEmailError(dto), model);

        assertEquals("client/newClient", view);
        assertEquals(true, model.getAttribute("editing"));
        assertNull(dto.getPassword());
        assertNull(dto.getConfirmPassword());
        verifyNoInteractions(clientService);
    }

    // ---------- delete ----------

    @Test
    void delete_redirectsWithTheDeletedFlag() {
        assertEquals("redirect:/clients?deleted", controller.delete(5L));

        verify(clientService).delete(5L);
    }

    @Test
    void delete_clientWithOrders_redirectsWithTheFailedFlag() {
        doThrow(ResourceInUseException.client()).when(clientService).delete(1L);

        assertEquals("redirect:/clients?deleteFailed", controller.delete(1L));
    }

    @Test
    void delete_unknownId_propagatesNotFound() {
        doThrow(ResourceNotFoundException.client(9L)).when(clientService).delete(9L);

        assertThrows(ResourceNotFoundException.class, () -> controller.delete(9L));
    }

    // ---------- form object ----------

    @Test
    void clientForm_takesTheIdFromTheUrl() {
        assertEquals(7L, controller.clientForm(7L).getId());
    }

    @Test
    void clientForm_withoutAnId_isANewClient() {
        assertNull(controller.clientForm(null).getId());
    }
}

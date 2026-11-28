package com.epam.rd.autocode.assessment.appliances.controller;

import com.epam.rd.autocode.assessment.appliances.dto.ApplianceDto;
import com.epam.rd.autocode.assessment.appliances.dto.ClientDto;
import com.epam.rd.autocode.assessment.appliances.dto.OrderDto;
import com.epam.rd.autocode.assessment.appliances.dto.OrderRowDto;
import com.epam.rd.autocode.assessment.appliances.exception.EmptyOrderException;
import com.epam.rd.autocode.assessment.appliances.exception.OrderAlreadyApprovedException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceNotFoundException;
import com.epam.rd.autocode.assessment.appliances.security.AppUserDetails;
import com.epam.rd.autocode.assessment.appliances.service.ApplianceService;
import com.epam.rd.autocode.assessment.appliances.service.ClientService;
import com.epam.rd.autocode.assessment.appliances.service.OrderService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;
    @Mock
    private ApplianceService applianceService;
    @Mock
    private ClientService clientService;

    private OrderController controller;
    private Model model;

    private final AppUserDetails employee = new AppUserDetails(1L, "Phobos", "phobos@gmail.com", "HASH", "EMPLOYEE");
    private final AppUserDetails mercury = new AppUserDetails(1L, "Mercury", "mercury@gmail.com", "HASH", "CLIENT");
    private final AppUserDetails venus = new AppUserDetails(2L, "Venus", "venus@gmail.com", "HASH", "CLIENT");

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderService, applianceService, clientService);
        model = new ConcurrentModel();
    }

    // ---------- helpers ----------

    private OrderRowDto row(Long applianceId, Long number) {
        OrderRowDto row = new OrderRowDto();
        row.setApplianceId(applianceId);
        row.setNumber(number);
        return row;
    }

    private OrderDto order(Long id, Long clientId, boolean approved, OrderRowDto... rows) {
        OrderDto dto = new OrderDto();
        dto.setId(id);
        dto.setClientId(clientId);
        dto.setClientName("Client " + clientId);
        dto.setApproved(approved);
        dto.setRows(new ArrayList<>(List.of(rows)));
        return dto;
    }

    private BindingResult cleanResult(OrderDto dto) {
        return new BeanPropertyBindingResult(dto, "order");
    }

    private BindingResult resultWithRowError(OrderDto dto) {
        BindingResult result = cleanResult(dto);
        result.rejectValue("rows", "some.error");
        return result;
    }

    private void stubAppliances() {
        when(applianceService.findAll()).thenReturn(List.of(new ApplianceDto()));
    }

    private void stubClients() {
        when(clientService.findAll()).thenReturn(List.of(new ClientDto()));
    }

    private OrderDto modelOrder() {
        return (OrderDto) model.getAttribute("order");
    }

    private boolean modelHasNoErrors() {
        BindingResult result = (BindingResult) model.getAttribute(BindingResult.MODEL_KEY_PREFIX + "order");
        return result != null && !result.hasErrors();
    }

    // ---------- list ----------

    @Test
    void list_forAnEmployee_showsAllOrders_newestFirst() {
        when(orderService.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        String view = controller.list(0, 10, "id", "desc", employee, model);

        assertEquals("order/orders", view);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderService).findAll(captor.capture());
        assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("id").getDirection());
        verify(orderService, never()).findByClientEmail(any(), any());
        assertEquals(true, model.getAttribute("employeeView"));
        assertEquals("", model.getAttribute("query"));
        assertEquals(PagingSupport.PAGE_SIZES, model.getAttribute("pageSizes"));
    }

    @Test
    void list_forAClient_showsOnlyTheirOwnOrders() {
        when(orderService.findByClientEmail(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.list(0, 10, "id", "desc", mercury, model);

        verify(orderService).findByClientEmail(org.mockito.ArgumentMatchers.eq("mercury@gmail.com"), any(Pageable.class));
        verify(orderService, never()).findAll(any());
        assertEquals(false, model.getAttribute("employeeView"));
    }

    @Test
    void list_passesPageSizeAndSort_andIgnoresHostileValues() {
        when(orderService.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.list(2, 20, "client.name", "asc", employee, model);
        assertEquals("client.name", model.getAttribute("sortField"));
        assertEquals(20, model.getAttribute("size"));

        controller.list(-3, 7, "employee.password", "sideways", employee, model);
        assertEquals("id", model.getAttribute("sortField"));
        assertEquals(PagingSupport.DEFAULT_SIZE, model.getAttribute("size"));
    }

    @Test
    void list_withoutAUser_isRefused() {
        assertThrows(AccessDeniedException.class, () -> controller.list(0, 10, "id", "desc", null, model));
    }

    // ---------- details ----------

    @Test
    void details_anEmployeeCanOpenAnyOrder() {
        when(orderService.findById(5L)).thenReturn(order(5L, 2L, false, row(1L, 1L)));

        String view = controller.details(5L, employee, model);

        assertEquals("order/viewOrder", view);
        assertEquals(5L, modelOrder().getId());
        assertEquals(true, model.getAttribute("employeeView"));
    }

    @Test
    void details_aClientCanOpenTheirOwnOrder() {
        when(orderService.findById(5L)).thenReturn(order(5L, 1L, false, row(1L, 1L)));

        assertEquals("order/viewOrder", controller.details(5L, mercury, model));
        assertEquals(false, model.getAttribute("employeeView"));
    }

    @Test
    void details_aClientCannotOpenSomebodyElsesOrder_andGetsNotFound() {
        when(orderService.findById(5L)).thenReturn(order(5L, 2L, false, row(1L, 1L)));

        assertThrows(ResourceNotFoundException.class, () -> controller.details(5L, mercury, model));
    }

    @Test
    void details_unknownOrder_propagatesNotFound() {
        when(orderService.findById(9L)).thenThrow(ResourceNotFoundException.order(9L));

        assertThrows(ResourceNotFoundException.class, () -> controller.details(9L, employee, model));
    }

    // ---------- new form ----------

    @Test
    void newForm_forAnEmployee_offersTheClientsAndAppliances() {
        stubAppliances();
        stubClients();

        String view = controller.newForm(null, employee, model);

        assertEquals("order/newOrder", view);
        assertEquals(1, modelOrder().getRows().size());
        assertEquals(1L, modelOrder().getRows().get(0).getNumber());
        assertNull(modelOrder().getClientId());
        assertNotNull(model.getAttribute("clients"));
        assertNotNull(model.getAttribute("appliances"));
        assertEquals(false, model.getAttribute("editing"));
    }

    @Test
    void newForm_forAClient_isForThemselves_withoutAClientDropdown() {
        stubAppliances();

        controller.newForm(null, mercury, model);

        assertEquals(1L, modelOrder().getClientId());
        assertNull(model.getAttribute("clients"));
        verifyNoInteractions(clientService);
    }

    @Test
    void newForm_preselectsTheApplianceFromTheOrderButton() {
        stubAppliances();

        controller.newForm(7L, mercury, model);

        assertEquals(7L, modelOrder().getRows().get(0).getApplianceId());
    }

    // ---------- create ----------

    @Test
    void create_forAClient_usesTheirOwnId_whateverTheFormSays() {
        OrderDto dto = order(null, 2L, false, row(1L, 2L)); // the form claims client 2
        OrderDto saved = order(9L, 1L, false, row(1L, 2L));
        when(orderService.create(dto)).thenReturn(saved);

        String view = controller.create(dto, cleanResult(dto), null, null, mercury, model);

        assertEquals("redirect:/orders/9?created", view);
        assertEquals(1L, dto.getClientId());
    }

    @Test
    void create_forAnEmployee_withAClient_savesAndRedirectsToTheNewOrder() {
        OrderDto dto = order(null, 2L, false, row(1L, 2L));
        when(orderService.create(dto)).thenReturn(order(9L, 2L, false, row(1L, 2L)));

        assertEquals("redirect:/orders/9?created", controller.create(dto, cleanResult(dto), null, null, employee, model));
    }

    @Test
    void create_forAnEmployee_withoutAClient_showsAnErrorOnTheClientField() {
        stubAppliances();
        stubClients();
        OrderDto dto = order(null, null, false, row(1L, 2L));
        BindingResult result = cleanResult(dto);

        String view = controller.create(dto, result, null, null, employee, model);

        assertEquals("order/newOrder", view);
        assertTrue(result.hasFieldErrors("clientId"));
        verify(orderService, never()).create(any());
        assertNotNull(model.getAttribute("clients")); // the dropdowns are filled again
    }

    @Test
    void create_withValidationErrors_showsTheFormAgainAndSavesNothing() {
        stubAppliances();
        OrderDto dto = order(null, null, false, row(null, null));

        String view = controller.create(dto, resultWithRowError(dto), null, null, mercury, model);

        assertEquals("order/newOrder", view);
        verify(orderService, never()).create(any());
        assertNotNull(model.getAttribute("appliances"));
    }

    @Test
    void create_addRow_addsABlankRowAndDropsTheErrors_withoutSaving() {
        stubAppliances();
        OrderDto dto = order(null, null, false, row(null, null));

        String view = controller.create(dto, resultWithRowError(dto), "1", null, mercury, model);

        assertEquals("order/newOrder", view);
        assertEquals(2, modelOrder().getRows().size());
        assertEquals(1L, modelOrder().getRows().get(1).getNumber());
        assertTrue(modelHasNoErrors());
        verify(orderService, never()).create(any());
    }

    @Test
    void create_addRow_stopsAtTheMaximumNumberOfRows() {
        stubAppliances();
        OrderDto dto = order(null, null, false);
        for (int i = 0; i < OrderController.MAX_ROWS; i++) {
            dto.getRows().add(row(1L, 1L));
        }

        controller.create(dto, cleanResult(dto), "1", null, mercury, model);

        assertEquals(OrderController.MAX_ROWS, modelOrder().getRows().size());
    }

    @Test
    void create_removeRow_removesThatRow_withoutSaving() {
        stubAppliances();
        OrderDto dto = order(null, null, false, row(1L, 1L), row(2L, 3L), row(3L, 5L));

        controller.create(dto, cleanResult(dto), null, 1, mercury, model);

        assertEquals(2, modelOrder().getRows().size());
        assertEquals(1L, modelOrder().getRows().get(0).getApplianceId());
        assertEquals(3L, modelOrder().getRows().get(1).getApplianceId());
        verify(orderService, never()).create(any());
    }

    @Test
    void create_removeRow_withAnIndexThatDoesNotExist_changesNothing() {
        stubAppliances();
        OrderDto dto = order(null, null, false, row(1L, 1L));

        controller.create(dto, cleanResult(dto), null, 7, mercury, model);

        assertEquals(1, modelOrder().getRows().size());
    }

    @Test
    void create_removeRow_ofTheLastRow_leavesOneBlankRow() {
        stubAppliances();
        OrderDto dto = order(null, null, false, row(1L, 4L));

        controller.create(dto, cleanResult(dto), null, 0, mercury, model);

        assertEquals(1, modelOrder().getRows().size());
        assertNull(modelOrder().getRows().get(0).getApplianceId());
        assertEquals(1L, modelOrder().getRows().get(0).getNumber());
    }

    @Test
    void create_withNoRowsAtAll_propagatesTheServicesEmptyOrderError() {
        OrderDto dto = order(null, 1L, false);
        when(orderService.create(dto)).thenThrow(new EmptyOrderException());

        assertThrows(EmptyOrderException.class, () -> controller.create(dto, cleanResult(dto), null, null, mercury, model));
    }

    // ---------- edit ----------

    @Test
    void editForm_showsAPendingOrderOfTheClient() {
        stubAppliances();
        when(orderService.findById(5L)).thenReturn(order(5L, 1L, false, row(1L, 2L)));

        String view = controller.editForm(5L, mercury, model);

        assertEquals("order/editOrder", view);
        assertEquals(true, model.getAttribute("editing"));
        assertEquals(5L, modelOrder().getId());
        verifyNoInteractions(clientService); // the client of an order cannot be changed
    }

    @Test
    void editForm_ofAnApprovedOrder_isRefused() {
        when(orderService.findById(5L)).thenReturn(order(5L, 1L, true, row(1L, 2L)));

        assertThrows(OrderAlreadyApprovedException.class, () -> controller.editForm(5L, mercury, model));
    }

    @Test
    void editForm_ofSomebodyElsesOrder_isNotFound() {
        when(orderService.findById(5L)).thenReturn(order(5L, 2L, false, row(1L, 2L)));

        assertThrows(ResourceNotFoundException.class, () -> controller.editForm(5L, mercury, model));
    }

    @Test
    void update_savesTheRowsAndKeepsTheOwner() {
        when(orderService.findById(5L)).thenReturn(order(5L, 1L, false, row(1L, 2L)));
        OrderDto dto = order(null, 2L, false, row(2L, 1L)); // the form claims another client
        when(orderService.update(5L, dto)).thenReturn(dto);

        String view = controller.update(5L, dto, cleanResult(dto), null, null, mercury, model);

        assertEquals("redirect:/orders/5?updated", view);
        assertEquals(5L, dto.getId());
        assertEquals(1L, dto.getClientId());
        verify(orderService).update(5L, dto);
    }

    @Test
    void update_ofAnApprovedOrder_isRefused() {
        when(orderService.findById(5L)).thenReturn(order(5L, 1L, true, row(1L, 2L)));
        OrderDto dto = order(null, null, false, row(2L, 1L));

        assertThrows(OrderAlreadyApprovedException.class,
                () -> controller.update(5L, dto, cleanResult(dto), null, null, mercury, model));
        verify(orderService, never()).update(any(), any());
    }

    @Test
    void update_ofSomebodyElsesOrder_isNotFound() {
        when(orderService.findById(5L)).thenReturn(order(5L, 2L, false, row(1L, 2L)));
        OrderDto dto = order(null, null, false, row(2L, 1L));

        assertThrows(ResourceNotFoundException.class,
                () -> controller.update(5L, dto, cleanResult(dto), null, null, mercury, model));
        verify(orderService, never()).update(any(), any());
    }

    @Test
    void update_withErrors_showsTheEditFormAgainAndSavesNothing() {
        stubAppliances();
        when(orderService.findById(5L)).thenReturn(order(5L, 1L, false, row(1L, 2L)));
        OrderDto dto = order(null, null, false, row(null, null));

        String view = controller.update(5L, dto, resultWithRowError(dto), null, null, mercury, model);

        assertEquals("order/editOrder", view);
        assertEquals(true, model.getAttribute("editing"));
        assertEquals("Client 1", modelOrder().getClientName());
        verify(orderService, never()).update(any(), any());
    }

    @Test
    void update_addRow_addsABlankRow_withoutSaving() {
        stubAppliances();
        when(orderService.findById(5L)).thenReturn(order(5L, 1L, false, row(1L, 2L)));
        OrderDto dto = order(null, null, false, row(1L, 2L));

        String view = controller.update(5L, dto, cleanResult(dto), "1", null, mercury, model);

        assertEquals("order/editOrder", view);
        assertEquals(2, modelOrder().getRows().size());
        verify(orderService, never()).update(any(), any());
    }

    // ---------- approve ----------

    @Test
    void approve_byAnEmployee_approvesTheOrderUnderTheirEmail() {
        assertEquals("redirect:/orders/5?approved", controller.approve(5L, employee));

        verify(orderService).approve(5L, "phobos@gmail.com");
    }

    @Test
    void approve_byAClient_isRefused() {
        assertThrows(AccessDeniedException.class, () -> controller.approve(5L, mercury));

        verifyNoInteractions(orderService);
    }

    @Test
    void approve_anOrderThatIsAlreadyApproved_redirectsWithAMessage() {
        when(orderService.approve(5L, "phobos@gmail.com")).thenThrow(new OrderAlreadyApprovedException());

        assertEquals("redirect:/orders/5?alreadyApproved", controller.approve(5L, employee));
    }

    @Test
    void approve_unknownOrder_propagatesNotFound() {
        when(orderService.approve(9L, "phobos@gmail.com")).thenThrow(ResourceNotFoundException.order(9L));

        assertThrows(ResourceNotFoundException.class, () -> controller.approve(9L, employee));
    }

    // ---------- delete ----------

    @Test
    void delete_aClientsOwnOrder_redirectsToTheList() {
        when(orderService.findById(5L)).thenReturn(order(5L, 1L, false, row(1L, 1L)));

        assertEquals("redirect:/orders?deleted", controller.delete(5L, mercury));

        verify(orderService).delete(5L);
    }

    @Test
    void delete_anyOrder_byAnEmployee() {
        when(orderService.findById(5L)).thenReturn(order(5L, 2L, false, row(1L, 1L)));

        assertEquals("redirect:/orders?deleted", controller.delete(5L, employee));
    }

    @Test
    void delete_somebodyElsesOrder_isNotFound_andNothingIsDeleted() {
        when(orderService.findById(5L)).thenReturn(order(5L, 2L, false, row(1L, 1L)));

        assertThrows(ResourceNotFoundException.class, () -> controller.delete(5L, mercury));

        verify(orderService, never()).delete(any());
    }

    @Test
    void delete_anApprovedOrder_redirectsWithAMessage() {
        when(orderService.findById(5L)).thenReturn(order(5L, 1L, true, row(1L, 1L)));
        doThrow(new OrderAlreadyApprovedException()).when(orderService).delete(5L);

        assertEquals("redirect:/orders/5?alreadyApproved", controller.delete(5L, mercury));
    }

    @Test
    void delete_unknownOrder_propagatesNotFound() {
        when(orderService.findById(9L)).thenThrow(ResourceNotFoundException.order(9L));

        assertThrows(ResourceNotFoundException.class, () -> controller.delete(9L, employee));
    }

    @Test
    void details_anotherClientCannotOpenTheOrder() {
        when(orderService.findById(5L)).thenReturn(order(5L, 1L, false, row(1L, 1L)));

        assertThrows(ResourceNotFoundException.class, () -> controller.details(5L, venus, model));
    }
}

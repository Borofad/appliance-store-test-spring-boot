package com.epam.rd.autocode.assessment.appliances.controller;

import com.epam.rd.autocode.assessment.appliances.dto.EmployeeDto;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceNotFoundException;
import com.epam.rd.autocode.assessment.appliances.security.AppUserDetails;
import com.epam.rd.autocode.assessment.appliances.service.EmployeeService;
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
class EmployeeControllerTest {

    @Mock
    private EmployeeService employeeService;

    private EmployeeController controller;
    private Model model;

    private final AppUserDetails phobos = new AppUserDetails(1L, "Phobos", "phobos@gmail.com", "HASH", "EMPLOYEE");
    private final AppUserDetails clientWithSameId = new AppUserDetails(1L, "Mercury", "mercury@gmail.com", "HASH", "CLIENT");

    @BeforeEach
    void setUp() {
        controller = new EmployeeController(employeeService);
        model = new ConcurrentModel();
    }

    private EmployeeDto employee(Long id, String password) {
        EmployeeDto dto = new EmployeeDto();
        dto.setId(id);
        dto.setName("Bob");
        dto.setEmail("bob@x.com");
        dto.setDepartment("sales");
        dto.setPassword(password);
        dto.setConfirmPassword(password);
        return dto;
    }

    private BindingResult cleanResult(EmployeeDto dto) {
        return new BeanPropertyBindingResult(dto, "employee");
    }

    private BindingResult resultWithEmailError(EmployeeDto dto) {
        BindingResult result = cleanResult(dto);
        result.rejectValue("email", "validation.user.email.unique");
        return result;
    }

    // ---------- list ----------

    @Test
    void list_searchesWithTheRequestedPageSizeAndSort_andFillsTheModel() {
        when(employeeService.search(eq("bo"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(employee(1L, null))));

        String view = controller.list("bo", 2, 20, "department", "desc", phobos, model);

        assertEquals("employee/employees", view);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(employeeService).search(eq("bo"), captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("department").getDirection());

        assertNotNull(model.getAttribute("page"));
        assertEquals("bo", model.getAttribute("query"));
        assertEquals(20, model.getAttribute("size"));
        assertEquals("department", model.getAttribute("sortField"));
        assertEquals("desc", model.getAttribute("sortDir"));
        assertEquals(PagingSupport.PAGE_SIZES, model.getAttribute("pageSizes"));
    }

    @Test
    void list_tellsThePageWhichRowIsTheLoggedInEmployee() {
        when(employeeService.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.list("", 0, 10, "name", "asc", phobos, model);

        assertEquals(1L, model.getAttribute("currentEmployeeId"));
    }

    @Test
    void list_aClientsIdIsNotMistakenForAnEmployeesId() {
        when(employeeService.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.list("", 0, 10, "name", "asc", clientWithSameId, model);

        assertNull(model.getAttribute("currentEmployeeId"));
    }

    @Test
    void list_withoutAUser_hasNoCurrentEmployee() {
        when(employeeService.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.list("", 0, 10, "name", "asc", null, model);

        assertNull(model.getAttribute("currentEmployeeId"));
    }

    @Test
    void list_doesNotAllowSortingByThePassword() {
        when(employeeService.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.list("", 0, 10, "password", "asc", phobos, model);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(employeeService).search(any(), captor.capture());
        assertNull(captor.getValue().getSort().getOrderFor("password"));
        assertEquals("name", model.getAttribute("sortField"));
    }

    @Test
    void list_ignoresHostileSizeAndPageValues_andCutsALongSearchText() {
        when(employeeService.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        controller.list("x".repeat(500), -3, 7, "name", "sideways", phobos, model);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(employeeService).search(query.capture(), pageable.capture());
        assertEquals(100, query.getValue().length());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(PagingSupport.DEFAULT_SIZE, pageable.getValue().getPageSize());
        assertEquals("asc", model.getAttribute("sortDir"));
    }

    // ---------- create ----------

    @Test
    void newForm_showsAnEmptyCreateForm() {
        assertEquals("employee/newEmployee", controller.newForm(model));
        assertEquals(false, model.getAttribute("editing"));
    }

    @Test
    void create_valid_savesAndRedirectsWithTheCreatedFlag() {
        EmployeeDto dto = employee(null, "Str0ng!Pass");

        String view = controller.create(dto, cleanResult(dto), model);

        assertEquals("redirect:/employees?created", view);
        verify(employeeService).create(dto);
    }

    @Test
    void create_withErrors_showsTheFormAgain_withoutSendingThePasswordsBack() {
        EmployeeDto dto = employee(null, "Str0ng!Pass");

        String view = controller.create(dto, resultWithEmailError(dto), model);

        assertEquals("employee/newEmployee", view);
        assertEquals(false, model.getAttribute("editing"));
        assertNull(dto.getPassword());
        assertNull(dto.getConfirmPassword());
        verifyNoInteractions(employeeService);
    }

    // ---------- edit ----------

    @Test
    void editForm_loadsTheEmployee() {
        when(employeeService.findById(5L)).thenReturn(employee(5L, null));

        String view = controller.editForm(5L, model);

        assertEquals("employee/newEmployee", view);
        assertEquals(true, model.getAttribute("editing"));
        assertEquals("Bob", ((EmployeeDto) model.getAttribute("employee")).getName());
    }

    @Test
    void editForm_unknownId_propagatesNotFound() {
        when(employeeService.findById(9L)).thenThrow(ResourceNotFoundException.employee(9L));

        assertThrows(ResourceNotFoundException.class, () -> controller.editForm(9L, model));
    }

    @Test
    void update_valid_savesAndRedirectsWithTheUpdatedFlag() {
        EmployeeDto dto = employee(5L, "");

        String view = controller.update(5L, dto, cleanResult(dto), model);

        assertEquals("redirect:/employees?updated", view);
        verify(employeeService).update(5L, dto);
    }

    @Test
    void update_withErrors_showsTheEditFormAgain_withoutSendingThePasswordsBack() {
        EmployeeDto dto = employee(5L, "N3w!Password");

        String view = controller.update(5L, dto, resultWithEmailError(dto), model);

        assertEquals("employee/newEmployee", view);
        assertEquals(true, model.getAttribute("editing"));
        assertNull(dto.getPassword());
        assertNull(dto.getConfirmPassword());
        verifyNoInteractions(employeeService);
    }

    // ---------- delete ----------

    @Test
    void delete_anotherEmployee_redirectsWithTheDeletedFlag() {
        assertEquals("redirect:/employees?deleted", controller.delete(4L, phobos));

        verify(employeeService).delete(4L);
    }

    @Test
    void delete_yourOwnAccount_isRefused() {
        assertEquals("redirect:/employees?deleteSelf", controller.delete(1L, phobos));

        verifyNoInteractions(employeeService);
    }

    @Test
    void delete_anEmployeeWhoseIdEqualsTheLoggedInClientsId_isAllowed() {
        assertEquals("redirect:/employees?deleted", controller.delete(1L, clientWithSameId));

        verify(employeeService).delete(1L);
    }

    @Test
    void delete_withoutAUser_stillGoesToTheService() {
        assertEquals("redirect:/employees?deleted", controller.delete(4L, null));

        verify(employeeService).delete(4L);
    }

    @Test
    void delete_employeeWhoApprovedOrders_redirectsWithTheFailedFlag() {
        doThrow(ResourceInUseException.employee()).when(employeeService).delete(2L);

        assertEquals("redirect:/employees?deleteFailed", controller.delete(2L, phobos));
    }

    @Test
    void delete_unknownId_propagatesNotFound() {
        doThrow(ResourceNotFoundException.employee(9L)).when(employeeService).delete(9L);

        assertThrows(ResourceNotFoundException.class, () -> controller.delete(9L, phobos));
    }

    // ---------- form object ----------

    @Test
    void employeeForm_takesTheIdFromTheUrl() {
        assertEquals(7L, controller.employeeForm(7L).getId());
    }

    @Test
    void employeeForm_withoutAnId_isANewEmployee() {
        assertNull(controller.employeeForm(null).getId());
    }
}

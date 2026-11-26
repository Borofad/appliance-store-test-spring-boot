package com.epam.rd.autocode.assessment.appliances.controller;

import com.epam.rd.autocode.assessment.appliances.dto.EmployeeDto;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException;
import com.epam.rd.autocode.assessment.appliances.security.AppUserDetails;
import com.epam.rd.autocode.assessment.appliances.service.EmployeeService;
import com.epam.rd.autocode.assessment.appliances.util.PagingSupport;
import com.epam.rd.autocode.assessment.appliances.validation.ValidationGroups.OnCreate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Set;

/**
 * Employees: list (search, pagination, sorting), create, edit, delete.
 * Same rules as clients (password required on create, empty password on edit keeps the current one,
 * passwords are never sent back to the browser). Additionally:
 * an employee cannot delete their own account (otherwise the last employee could lock everybody out).
 */
@Slf4j
@Controller
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController {

    static final String LIST_VIEW = "employee/employees";
    static final String FORM_VIEW = "employee/newEmployee";
    private static final String EMPLOYEE_ROLE = "EMPLOYEE";
    private static final Set<String> SORT_FIELDS = Set.of("id", "name", "email", "department");
    private static final int MAX_QUERY_LENGTH = 100;

    private final EmployeeService employeeService;

    /** The id comes from the URL, never from the form. */
    @InitBinder("employee")
    void initBinder(WebDataBinder binder) {
        binder.setDisallowedFields("id");
    }

    /** For /employees/{id} the id is set before binding, so "email must be unique" ignores the employee being edited. */
    @ModelAttribute("employee")
    EmployeeDto employeeForm(@PathVariable(name = "id", required = false) Long id) {
        EmployeeDto dto = new EmployeeDto();
        dto.setId(id);
        return dto;
    }

    @GetMapping
    public String list(@RequestParam(name = "query", defaultValue = "") String query,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "size", defaultValue = "10") int size,
                       @RequestParam(name = "sortField", defaultValue = "name") String sortField,
                       @RequestParam(name = "sortDir", defaultValue = "asc") String sortDir,
                       @AuthenticationPrincipal AppUserDetails currentUser,
                       Model model) {
        String safeQuery = query.length() > MAX_QUERY_LENGTH ? query.substring(0, MAX_QUERY_LENGTH) : query;
        Pageable pageable = PagingSupport.pageable(page, size, sortField, sortDir, SORT_FIELDS, "name");
        Page<EmployeeDto> result = employeeService.search(safeQuery, pageable);

        model.addAttribute("page", result);
        model.addAttribute("query", safeQuery);
        model.addAttribute("size", pageable.getPageSize());
        model.addAttribute("sortField", PagingSupport.sortField(pageable));
        model.addAttribute("sortDir", PagingSupport.sortDir(pageable));
        model.addAttribute("pageSizes", PagingSupport.PAGE_SIZES);
        // lets the page hide the Delete button on the logged-in employee's own row
        model.addAttribute("currentEmployeeId", isEmployee(currentUser) ? currentUser.getId() : null);
        return LIST_VIEW;
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("editing", false);
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@Validated(OnCreate.class) @ModelAttribute("employee") EmployeeDto dto,
                         BindingResult result, Model model) {
        if (result.hasErrors()) {
            return redisplay(dto, model, false);
        }
        employeeService.create(dto);
        return "redirect:/employees?created";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        model.addAttribute("employee", employeeService.findById(id));
        model.addAttribute("editing", true);
        return FORM_VIEW;
    }

    @PostMapping("/{id}")
    public String update(@PathVariable("id") Long id,
                         @Valid @ModelAttribute("employee") EmployeeDto dto,
                         BindingResult result, Model model) {
        if (result.hasErrors()) {
            return redisplay(dto, model, true);
        }
        employeeService.update(id, dto);
        return "redirect:/employees?updated";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id, @AuthenticationPrincipal AppUserDetails currentUser) {
        // client and employee ids overlap, so the role must be checked as well
        if (isEmployee(currentUser) && id.equals(currentUser.getId())) {
            log.warn("Employee id={} tried to delete their own account", id);
            return "redirect:/employees?deleteSelf";
        }
        try {
            employeeService.delete(id);
            return "redirect:/employees?deleted";
        } catch (ResourceInUseException ex) {
            return "redirect:/employees?deleteFailed";
        }
    }

    private boolean isEmployee(AppUserDetails user) {
        return user != null && EMPLOYEE_ROLE.equals(user.getRole());
    }

    /** Shows the form again after a failed submit, without sending the typed passwords back. */
    private String redisplay(EmployeeDto dto, Model model, boolean editing) {
        dto.setPassword(null);
        dto.setConfirmPassword(null);
        model.addAttribute("editing", editing);
        return FORM_VIEW;
    }
}

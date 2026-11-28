package com.epam.rd.autocode.assessment.appliances.controller;

import com.epam.rd.autocode.assessment.appliances.dto.OrderDto;
import com.epam.rd.autocode.assessment.appliances.dto.OrderRowDto;
import com.epam.rd.autocode.assessment.appliances.exception.OrderAlreadyApprovedException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceNotFoundException;
import com.epam.rd.autocode.assessment.appliances.security.AppUserDetails;
import com.epam.rd.autocode.assessment.appliances.service.ApplianceService;
import com.epam.rd.autocode.assessment.appliances.service.ClientService;
import com.epam.rd.autocode.assessment.appliances.service.OrderService;
import com.epam.rd.autocode.assessment.appliances.util.PagingSupport;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Objects;
import java.util.Set;

/**
 * Orders. Clients see and manage only their OWN orders; employees see all orders and are the only ones who
 * can approve. An approved order can no longer be changed or deleted (the service enforces it).
 *
 * The order form is stateless: the whole form, including every row, is posted each time. The "Add item" and
 * "Remove" buttons post the form back with an action and the page is shown again (no server session needed).
 * A client who asks for somebody else's order gets "not found", so nobody can probe which order numbers exist.
 */
@Slf4j
@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    static final String LIST_VIEW = "order/orders";
    static final String DETAILS_VIEW = "order/viewOrder";
    static final String NEW_VIEW = "order/newOrder";
    static final String EDIT_VIEW = "order/editOrder";
    static final int MAX_ROWS = 50;
    private static final String EMPLOYEE = "EMPLOYEE";
    private static final String CLIENT = "CLIENT";
    private static final Set<String> SORT_FIELDS = Set.of("id", "approved", "client.name");

    private final OrderService orderService;
    private final ApplianceService applianceService;
    private final ClientService clientService;

    /**
     * Only these form fields are ever read. Everything else a browser might send (approved, employeeId,
     * row amounts, ids, ...) is ignored: prices and approval are decided on the server.
     */
    @InitBinder("order")
    void initBinder(WebDataBinder binder) {
        binder.setAllowedFields("clientId", "rows[*].applianceId", "rows[*].number");
    }

    // ---------- list and details ----------

    @GetMapping
    public String list(@RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "size", defaultValue = "10") int size,
                       @RequestParam(name = "sortField", defaultValue = "id") String sortField,
                       @RequestParam(name = "sortDir", defaultValue = "desc") String sortDir,
                       @AuthenticationPrincipal AppUserDetails user,
                       Model model) {
        requireUser(user);
        Pageable pageable = PagingSupport.pageable(page, size, sortField, sortDir, SORT_FIELDS, "id");
        Page<OrderDto> result = isEmployee(user)
                ? orderService.findAll(pageable)
                : orderService.findByClientEmail(user.getEmail(), pageable);

        model.addAttribute("page", result);
        model.addAttribute("query", "");
        model.addAttribute("size", pageable.getPageSize());
        model.addAttribute("sortField", PagingSupport.sortField(pageable));
        model.addAttribute("sortDir", PagingSupport.sortDir(pageable));
        model.addAttribute("pageSizes", PagingSupport.PAGE_SIZES);
        model.addAttribute("employeeView", isEmployee(user));
        return LIST_VIEW;
    }

    @GetMapping("/{id}")
    public String details(@PathVariable("id") Long id,
                          @AuthenticationPrincipal AppUserDetails user,
                          Model model) {
        model.addAttribute("order", loadAuthorized(id, user));
        model.addAttribute("employeeView", isEmployee(user));
        return DETAILS_VIEW;
    }

    // ---------- create ----------

    /** An optional ?applianceId=5 pre-selects that appliance in the first row (the "Order" button on the appliances list). */
    @GetMapping("/new")
    public String newForm(@RequestParam(name = "applianceId", required = false) Long applianceId,
                          @AuthenticationPrincipal AppUserDetails user,
                          Model model) {
        requireUser(user);
        OrderDto order = new OrderDto();
        OrderRowDto row = newRow();
        row.setApplianceId(applianceId);
        order.getRows().add(row);
        if (isClient(user)) {
            order.setClientId(user.getId());
        }
        model.addAttribute("order", order);
        prepareForm(model, user, false);
        return NEW_VIEW;
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("order") OrderDto dto,
                         BindingResult result,
                         @RequestParam(name = "addRow", required = false) String addRow,
                         @RequestParam(name = "removeRow", required = false) Integer removeRow,
                         @AuthenticationPrincipal AppUserDetails user,
                         Model model) {
        requireUser(user);
        if (isClient(user)) {
            dto.setClientId(user.getId()); // a client can only order for themselves, whatever the form says
        }
        if (applyRowAction(dto, addRow, removeRow)) {
            return redisplay(dto, model, user, false, true);
        }
        if (isEmployee(user) && dto.getClientId() == null) {
            result.rejectValue("clientId", "validation.orders.client.required");
        }
        if (result.hasErrors()) {
            return redisplay(dto, model, user, false, false);
        }
        OrderDto saved = orderService.create(dto);
        return "redirect:/orders/" + saved.getId() + "?created";
    }

    // ---------- edit ----------

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id,
                           @AuthenticationPrincipal AppUserDetails user,
                           Model model) {
        OrderDto order = loadAuthorized(id, user);
        if (Boolean.TRUE.equals(order.getApproved())) {
            throw new OrderAlreadyApprovedException();
        }
        model.addAttribute("order", order);
        prepareForm(model, user, true);
        return EDIT_VIEW;
    }

    @PostMapping("/{id}")
    public String update(@PathVariable("id") Long id,
                         @Valid @ModelAttribute("order") OrderDto dto,
                         BindingResult result,
                         @RequestParam(name = "addRow", required = false) String addRow,
                         @RequestParam(name = "removeRow", required = false) Integer removeRow,
                         @AuthenticationPrincipal AppUserDetails user,
                         Model model) {
        OrderDto existing = loadAuthorized(id, user);
        if (Boolean.TRUE.equals(existing.getApproved())) {
            throw new OrderAlreadyApprovedException();
        }
        // the owner of an order never changes; these are only needed to show the form again
        dto.setId(id);
        dto.setClientId(existing.getClientId());
        dto.setClientName(existing.getClientName());

        if (applyRowAction(dto, addRow, removeRow)) {
            return redisplay(dto, model, user, true, true);
        }
        if (result.hasErrors()) {
            return redisplay(dto, model, user, true, false);
        }
        orderService.update(id, dto);
        return "redirect:/orders/" + id + "?updated";
    }

    // ---------- approve and delete ----------

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable("id") Long id, @AuthenticationPrincipal AppUserDetails user) {
        if (!isEmployee(requireUser(user))) {
            log.warn("SECURITY: client id={} tried to approve order id={}", user.getId(), id);
            throw new AccessDeniedException("Only employees can approve orders");
        }
        try {
            orderService.approve(id, user.getEmail());
            return "redirect:/orders/" + id + "?approved";
        } catch (OrderAlreadyApprovedException ex) {
            return "redirect:/orders/" + id + "?alreadyApproved";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id, @AuthenticationPrincipal AppUserDetails user) {
        loadAuthorized(id, user); // a client may only delete their own order
        try {
            orderService.delete(id);
            return "redirect:/orders?deleted";
        } catch (OrderAlreadyApprovedException ex) {
            return "redirect:/orders/" + id + "?alreadyApproved";
        }
    }

    // ---------- helpers ----------

    /** Loads the order; a client who does not own it gets "not found" (same as for a missing order). */
    private OrderDto loadAuthorized(Long id, AppUserDetails user) {
        requireUser(user);
        OrderDto order = orderService.findById(id);
        boolean allowed = isEmployee(user)
                || (isClient(user) && Objects.equals(order.getClientId(), user.getId()));
        if (!allowed) {
            log.warn("SECURITY: client id={} tried to open order id={} of another client", user.getId(), id);
            throw ResourceNotFoundException.order(id);
        }
        return order;
    }

    /** Handles the "Add item" / "Remove" buttons. Returns true if one of them was pressed. */
    private boolean applyRowAction(OrderDto dto, String addRow, Integer removeRow) {
        if (addRow != null) {
            if (dto.getRows().size() < MAX_ROWS) {
                dto.getRows().add(newRow());
            }
            return true;
        }
        if (removeRow != null) {
            if (removeRow >= 0 && removeRow < dto.getRows().size()) {
                dto.getRows().remove(removeRow.intValue());
            }
            if (dto.getRows().isEmpty()) {
                dto.getRows().add(newRow()); // the form always shows at least one row
            }
            return true;
        }
        return false;
    }

    /**
     * Shows the form again. After an "Add item" / "Remove" click the validation errors are dropped, because the
     * user has not tried to save yet (half-filled rows would otherwise be shown as errors).
     */
    private String redisplay(OrderDto dto, Model model, AppUserDetails user, boolean editing, boolean clearErrors) {
        model.addAttribute("order", dto);
        if (clearErrors) {
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "order", new BeanPropertyBindingResult(dto, "order"));
        }
        prepareForm(model, user, editing);
        return editing ? EDIT_VIEW : NEW_VIEW;
    }

    /** The dropdown contents. Only employees choose the client, and only when creating an order. */
    private void prepareForm(Model model, AppUserDetails user, boolean editing) {
        model.addAttribute("editing", editing);
        model.addAttribute("employeeView", isEmployee(user));
        model.addAttribute("appliances", applianceService.findAll());
        if (isEmployee(user) && !editing) {
            model.addAttribute("clients", clientService.findAll());
        }
    }

    private static OrderRowDto newRow() {
        OrderRowDto row = new OrderRowDto();
        row.setNumber(1L);
        return row;
    }

    private static AppUserDetails requireUser(AppUserDetails user) {
        if (user == null) {
            throw new AccessDeniedException("Login required");
        }
        return user;
    }

    private static boolean isEmployee(AppUserDetails user) {
        return user != null && EMPLOYEE.equals(user.getRole());
    }

    private static boolean isClient(AppUserDetails user) {
        return user != null && CLIENT.equals(user.getRole());
    }
}

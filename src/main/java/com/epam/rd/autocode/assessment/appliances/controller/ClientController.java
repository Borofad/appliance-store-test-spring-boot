package com.epam.rd.autocode.assessment.appliances.controller;

import com.epam.rd.autocode.assessment.appliances.dto.ClientDto;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException;
import com.epam.rd.autocode.assessment.appliances.service.ClientService;
import com.epam.rd.autocode.assessment.appliances.util.PagingSupport;
import com.epam.rd.autocode.assessment.appliances.validation.ValidationGroups.OnCreate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Clients: list (search, pagination, sorting), create, edit, delete.
 *
 * Passwords: required when creating a client (validation group OnCreate); when editing, an empty
 * password means "keep the current one". Passwords are never sent back to the browser: after a failed
 * submit they are cleared from the form object.
 */
@Slf4j
@Controller
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientController {

    static final String LIST_VIEW = "client/clients";
    static final String FORM_VIEW = "client/newClient";
    private static final Set<String> SORT_FIELDS = Set.of("id", "name", "email", "card");
    private static final int MAX_QUERY_LENGTH = 100;

    private final ClientService clientService;

    /** The id comes from the URL, never from the form. */
    @InitBinder("client")
    void initBinder(WebDataBinder binder) {
        binder.setDisallowedFields("id");
    }

    /** For /clients/{id} the id is set before binding, so "email must be unique" ignores the client being edited. */
    @ModelAttribute("client")
    ClientDto clientForm(@PathVariable(name = "id", required = false) Long id) {
        ClientDto dto = new ClientDto();
        dto.setId(id);
        return dto;
    }

    @GetMapping
    public String list(@RequestParam(name = "query", defaultValue = "") String query,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "size", defaultValue = "10") int size,
                       @RequestParam(name = "sortField", defaultValue = "name") String sortField,
                       @RequestParam(name = "sortDir", defaultValue = "asc") String sortDir,
                       Model model) {
        String safeQuery = query.length() > MAX_QUERY_LENGTH ? query.substring(0, MAX_QUERY_LENGTH) : query;
        Pageable pageable = PagingSupport.pageable(page, size, sortField, sortDir, SORT_FIELDS, "name");
        Page<ClientDto> result = clientService.search(safeQuery, pageable);

        model.addAttribute("page", result);
        model.addAttribute("query", safeQuery);
        model.addAttribute("size", pageable.getPageSize());
        model.addAttribute("sortField", PagingSupport.sortField(pageable));
        model.addAttribute("sortDir", PagingSupport.sortDir(pageable));
        model.addAttribute("pageSizes", PagingSupport.PAGE_SIZES);
        return LIST_VIEW;
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("editing", false);
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@Validated(OnCreate.class) @ModelAttribute("client") ClientDto dto,
                         BindingResult result, Model model) {
        if (result.hasErrors()) {
            return redisplay(dto, model, false);
        }
        clientService.create(dto);
        return "redirect:/clients?created";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        model.addAttribute("client", clientService.findById(id));
        model.addAttribute("editing", true);
        return FORM_VIEW;
    }

    @PostMapping("/{id}")
    public String update(@PathVariable("id") Long id,
                         @Valid @ModelAttribute("client") ClientDto dto,
                         BindingResult result, Model model) {
        if (result.hasErrors()) {
            return redisplay(dto, model, true);
        }
        clientService.update(id, dto);
        return "redirect:/clients?updated";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id) {
        try {
            clientService.delete(id);
            return "redirect:/clients?deleted";
        } catch (ResourceInUseException ex) {
            return "redirect:/clients?deleteFailed";
        }
    }

    /** Shows the form again after a failed submit, without sending the typed passwords back. */
    private String redisplay(ClientDto dto, Model model, boolean editing) {
        dto.setPassword(null);
        dto.setConfirmPassword(null);
        model.addAttribute("editing", editing);
        return FORM_VIEW;
    }
}

package com.epam.rd.autocode.assessment.appliances.controller;

import com.epam.rd.autocode.assessment.appliances.dto.ApplianceDto;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException;
import com.epam.rd.autocode.assessment.appliances.model.Category;
import com.epam.rd.autocode.assessment.appliances.model.PowerType;
import com.epam.rd.autocode.assessment.appliances.service.ApplianceService;
import com.epam.rd.autocode.assessment.appliances.service.ManufacturerService;
import com.epam.rd.autocode.assessment.appliances.util.PagingSupport;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
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
 * Appliances: list (search, pagination, sorting), create, edit, delete.
 * Same pattern as ManufacturerController; the form additionally needs the dropdown contents.
 */
@Slf4j
@Controller
@RequestMapping("/appliances")
@RequiredArgsConstructor
public class ApplianceController {

    static final String LIST_VIEW = "appliance/appliances";
    static final String FORM_VIEW = "appliance/newAppliance";
    private static final Set<String> SORT_FIELDS =
            Set.of("id", "name", "category", "model", "price", "power", "manufacturer.name");
    private static final int MAX_QUERY_LENGTH = 100;

    private final ApplianceService applianceService;
    private final ManufacturerService manufacturerService;

    /** The id comes from the URL and the manufacturer name is display-only: neither is read from the form. */
    @InitBinder("appliance")
    void initBinder(WebDataBinder binder) {
        binder.setDisallowedFields("id", "manufacturerName");
    }

    @ModelAttribute("appliance")
    ApplianceDto applianceForm(@PathVariable(name = "id", required = false) Long id) {
        ApplianceDto dto = new ApplianceDto();
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
        Page<ApplianceDto> result = applianceService.search(safeQuery, pageable);

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
        prepareForm(model, false);
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("appliance") ApplianceDto dto,
                         BindingResult result, Model model) {
        if (result.hasErrors()) {
            prepareForm(model, false);
            return FORM_VIEW;
        }
        applianceService.create(dto);
        return "redirect:/appliances?created";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        model.addAttribute("appliance", applianceService.findById(id));
        prepareForm(model, true);
        return FORM_VIEW;
    }

    @PostMapping("/{id}")
    public String update(@PathVariable("id") Long id,
                         @Valid @ModelAttribute("appliance") ApplianceDto dto,
                         BindingResult result, Model model) {
        if (result.hasErrors()) {
            prepareForm(model, true);
            return FORM_VIEW;
        }
        applianceService.update(id, dto);
        return "redirect:/appliances?updated";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id) {
        try {
            applianceService.delete(id);
            return "redirect:/appliances?deleted";
        } catch (ResourceInUseException ex) {
            return "redirect:/appliances?deleteFailed";
        }
    }

    /** The form needs the contents of its three dropdowns. */
    private void prepareForm(Model model, boolean editing) {
        model.addAttribute("editing", editing);
        model.addAttribute("manufacturers", manufacturerService.findAll());
        model.addAttribute("categories", Category.values());
        model.addAttribute("powerTypes", PowerType.values());
    }
}

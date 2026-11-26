package com.epam.rd.autocode.assessment.appliances.controller;

import com.epam.rd.autocode.assessment.appliances.dto.ManufacturerDto;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException;
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
 * Manufacturers: list (search, pagination, sorting), create, edit, delete.
 *
 * Result messages ("created", "deleted", ...) travel as a URL flag, e.g. /manufacturers?created,
 * so no server session is needed (flash attributes would create one).
 */
@Slf4j
@Controller
@RequestMapping("/manufacturers")
@RequiredArgsConstructor
public class ManufacturerController {

    static final String LIST_VIEW = "manufacture/manufacturers";
    static final String FORM_VIEW = "manufacture/newManufacturer";
    private static final Set<String> SORT_FIELDS = Set.of("id", "name");
    private static final int MAX_QUERY_LENGTH = 100;

    private final ManufacturerService manufacturerService;

    /** The id is never read from the form: it comes from the URL (see manufacturerForm). */
    @InitBinder("manufacturer")
    void initBinder(WebDataBinder binder) {
        binder.setDisallowedFields("id");
    }

    /**
     * Creates the form object before binding. For /manufacturers/{id} the id is taken from the URL, so the
     * "name must be unique" check knows which row is being edited and does not complain about the row itself.
     */
    @ModelAttribute("manufacturer")
    ManufacturerDto manufacturerForm(@PathVariable(name = "id", required = false) Long id) {
        ManufacturerDto dto = new ManufacturerDto();
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
        Page<ManufacturerDto> result = manufacturerService.search(safeQuery, pageable);

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
    public String create(@Valid @ModelAttribute("manufacturer") ManufacturerDto dto,
                         BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("editing", false);
            return FORM_VIEW;
        }
        manufacturerService.create(dto);
        return "redirect:/manufacturers?created";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        model.addAttribute("manufacturer", manufacturerService.findById(id));
        model.addAttribute("editing", true);
        return FORM_VIEW;
    }

    @PostMapping("/{id}")
    public String update(@PathVariable("id") Long id,
                         @Valid @ModelAttribute("manufacturer") ManufacturerDto dto,
                         BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("editing", true);
            return FORM_VIEW;
        }
        manufacturerService.update(id, dto);
        return "redirect:/manufacturers?updated";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id) {
        try {
            manufacturerService.delete(id);
            return "redirect:/manufacturers?deleted";
        } catch (ResourceInUseException ex) {
            return "redirect:/manufacturers?deleteFailed";
        }
    }
}

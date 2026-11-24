package com.epam.rd.autocode.assessment.appliances.service;

import com.epam.rd.autocode.assessment.appliances.dto.ManufacturerDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ManufacturerService {

    /** Paged, sorted search by name; an empty query returns everything. */
    Page<ManufacturerDto> search(String query, Pageable pageable);

    /** All manufacturers sorted by name (for dropdowns). */
    List<ManufacturerDto> findAll();

    ManufacturerDto findById(Long id);

    ManufacturerDto create(ManufacturerDto dto);

    ManufacturerDto update(Long id, ManufacturerDto dto);

    /** @throws com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException if appliances still use this manufacturer */
    void delete(Long id);
}
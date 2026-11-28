package com.epam.rd.autocode.assessment.appliances.service;

import com.epam.rd.autocode.assessment.appliances.dto.ApplianceDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ApplianceService {

    /** Paged, sorted search by appliance name, model or manufacturer name; empty query returns everything. */
    Page<ApplianceDto> search(String query, Pageable pageable);

    /** All appliances sorted by name (for dropdowns). */
    List<ApplianceDto> findAll();

    ApplianceDto findById(Long id);

    ApplianceDto create(ApplianceDto dto);

    ApplianceDto update(Long id, ApplianceDto dto);

    /** @throws com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException if the appliance is part of an order */
    void delete(Long id);
}
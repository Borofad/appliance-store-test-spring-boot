package com.epam.rd.autocode.assessment.appliances.service.impl;

import com.epam.rd.autocode.assessment.appliances.dto.ManufacturerDto;
import com.epam.rd.autocode.assessment.appliances.exception.ManufacturerAlreadyExistsException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceNotFoundException;
import com.epam.rd.autocode.assessment.appliances.model.Manufacturer;
import com.epam.rd.autocode.assessment.appliances.repository.ApplianceRepository;
import com.epam.rd.autocode.assessment.appliances.repository.ManufacturerRepository;
import com.epam.rd.autocode.assessment.appliances.service.ManufacturerService;
import com.epam.rd.autocode.assessment.appliances.service.SearchUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManufacturerServiceImpl implements ManufacturerService {

    private final ManufacturerRepository manufacturerRepository;
    private final ApplianceRepository applianceRepository;
    private final ModelMapper modelMapper;

    @Override
    public Page<ManufacturerDto> search(String query, Pageable pageable) {
        return manufacturerRepository
                .findByNameContainingIgnoreCase(SearchUtils.normalize(query), pageable)
                .map(this::toDto);
    }

    @Override
    public List<ManufacturerDto> findAll() {
        return manufacturerRepository.findAll(Sort.by("name")).stream().map(this::toDto).toList();
    }

    @Override
    public ManufacturerDto findById(Long id) {
        return toDto(getEntity(id));
    }

    @Override
    @Transactional
    public ManufacturerDto create(ManufacturerDto dto) {
        String name = dto.getName().trim();
        if (manufacturerRepository.existsByNameIgnoreCase(name)) {
            throw new ManufacturerAlreadyExistsException(name);
        }
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setName(name);
        Manufacturer saved = manufacturerRepository.save(manufacturer);
        log.info("Manufacturer created: id={}, name={}", saved.getId(), saved.getName());
        return toDto(saved);
    }

    @Override
    @Transactional
    public ManufacturerDto update(Long id, ManufacturerDto dto) {
        Manufacturer manufacturer = getEntity(id);
        String name = dto.getName().trim();
        if (manufacturerRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ManufacturerAlreadyExistsException(name);
        }
        manufacturer.setName(name);
        log.info("Manufacturer updated: id={}, name={}", id, name);
        return toDto(manufacturerRepository.save(manufacturer));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Manufacturer manufacturer = getEntity(id);
        if (applianceRepository.existsByManufacturerId(id)) {
            log.warn("Refused to delete manufacturer id={}: still used by appliances", id);
            throw ResourceInUseException.manufacturer();
        }
        manufacturerRepository.delete(manufacturer);
        log.info("Manufacturer deleted: id={}", id);
    }

    private Manufacturer getEntity(Long id) {
        return manufacturerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.manufacturer(id));
    }

    private ManufacturerDto toDto(Manufacturer manufacturer) {
        return modelMapper.map(manufacturer, ManufacturerDto.class);
    }
}

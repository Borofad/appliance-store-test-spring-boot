package com.epam.rd.autocode.assessment.appliances.service.impl;

import com.epam.rd.autocode.assessment.appliances.dto.ApplianceDto;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceNotFoundException;
import com.epam.rd.autocode.assessment.appliances.model.Appliance;
import com.epam.rd.autocode.assessment.appliances.model.Manufacturer;
import com.epam.rd.autocode.assessment.appliances.repository.ApplianceInOrderRepository;
import com.epam.rd.autocode.assessment.appliances.repository.ApplianceRepository;
import com.epam.rd.autocode.assessment.appliances.repository.ManufacturerRepository;
import com.epam.rd.autocode.assessment.appliances.service.ApplianceService;
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
public class ApplianceServiceImpl implements ApplianceService {

    private final ApplianceRepository applianceRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final ApplianceInOrderRepository applianceInOrderRepository;
    private final ModelMapper modelMapper;

    @Override
    public Page<ApplianceDto> search(String query, Pageable pageable) {
        return applianceRepository.search(SearchUtils.normalize(query), pageable).map(this::toDto);
    }

    @Override
    public List<ApplianceDto> findAll() {
        return applianceRepository.findAll(Sort.by("name")).stream().map(this::toDto).toList();
    }

    @Override
    public ApplianceDto findById(Long id) {
        return toDto(getEntity(id));
    }

    @Override
    @Transactional
    public ApplianceDto create(ApplianceDto dto) {
        Appliance appliance = new Appliance();
        apply(appliance, dto);
        Appliance saved = applianceRepository.save(appliance);
        log.info("Appliance created: id={}, name={}", saved.getId(), saved.getName());
        return toDto(saved);
    }

    @Override
    @Transactional
    public ApplianceDto update(Long id, ApplianceDto dto) {
        Appliance appliance = getEntity(id);
        apply(appliance, dto);
        log.info("Appliance updated: id={}", id);
        return toDto(applianceRepository.save(appliance));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Appliance appliance = getEntity(id);
        if (applianceInOrderRepository.existsByApplianceId(id)) {
            log.warn("Refused to delete appliance id={}: used in existing orders", id);
            throw ResourceInUseException.appliance();
        }
        applianceRepository.delete(appliance);
        log.info("Appliance deleted: id={}", id);
    }

    /** Copies the form values onto the entity; the manufacturer is loaded from the database, never built from the form. */
    private void apply(Appliance appliance, ApplianceDto dto) {
        Manufacturer manufacturer = manufacturerRepository.findById(dto.getManufacturerId())
                .orElseThrow(() -> ResourceNotFoundException.manufacturer(dto.getManufacturerId()));
        appliance.setName(dto.getName().trim());
        appliance.setCategory(dto.getCategory());
        appliance.setModel(dto.getModel().trim());
        appliance.setManufacturer(manufacturer);
        appliance.setPowerType(dto.getPowerType());
        appliance.setCharacteristic(dto.getCharacteristic());
        appliance.setDescription(dto.getDescription());
        appliance.setPower(dto.getPower());
        appliance.setPrice(dto.getPrice());
    }

    private Appliance getEntity(Long id) {
        return applianceRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.appliance(id));
    }

    private ApplianceDto toDto(Appliance appliance) {
        return modelMapper.map(appliance, ApplianceDto.class);
    }
}
package com.epam.rd.autocode.assessment.appliances.service.impl;

import com.epam.rd.autocode.assessment.appliances.config.MapperConfig;
import com.epam.rd.autocode.assessment.appliances.dto.ManufacturerDto;
import com.epam.rd.autocode.assessment.appliances.exception.ManufacturerAlreadyExistsException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceNotFoundException;
import com.epam.rd.autocode.assessment.appliances.model.Manufacturer;
import com.epam.rd.autocode.assessment.appliances.repository.ApplianceRepository;
import com.epam.rd.autocode.assessment.appliances.repository.ManufacturerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManufacturerServiceImplTest {

    @Mock
    private ManufacturerRepository manufacturerRepository;
    @Mock
    private ApplianceRepository applianceRepository;

    private ManufacturerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ManufacturerServiceImpl(manufacturerRepository, applianceRepository,
                new MapperConfig().modelMapper());
    }

    private Manufacturer manufacturer(Long id, String name) {
        return new Manufacturer(id, name);
    }

    private ManufacturerDto dto(Long id, String name) {
        return new ManufacturerDto(id, name);
    }

    @Test
    void search_nullQuery_searchesWithEmptyText() {
        Pageable pageable = PageRequest.of(0, 10);
        when(manufacturerRepository.findByNameContainingIgnoreCase("", pageable))
                .thenReturn(new PageImpl<>(List.of(manufacturer(1L, "Sony"))));

        Page<ManufacturerDto> result = service.search(null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Sony", result.getContent().get(0).getName());
    }

    @Test
    void search_trimsQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        when(manufacturerRepository.findByNameContainingIgnoreCase("son", pageable))
                .thenReturn(new PageImpl<>(List.of(manufacturer(1L, "Sony"))));

        assertEquals(1, service.search("  son ", pageable).getContent().size());
    }

    @Test
    void findAll_returnsAllMapped() {
        when(manufacturerRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(manufacturer(1L, "Acer"), manufacturer(2L, "Dell")));

        List<ManufacturerDto> result = service.findAll();

        assertEquals(2, result.size());
        assertEquals("Acer", result.get(0).getName());
    }

    @Test
    void findById_found() {
        when(manufacturerRepository.findById(1L)).thenReturn(Optional.of(manufacturer(1L, "Sony")));

        assertEquals("Sony", service.findById(1L).getName());
    }

    @Test
    void findById_notFound_throws() {
        when(manufacturerRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findById(9L));
    }

    @Test
    void create_savesTrimmedName() {
        when(manufacturerRepository.existsByNameIgnoreCase("Sony")).thenReturn(false);
        when(manufacturerRepository.save(any(Manufacturer.class))).thenAnswer(inv -> {
            Manufacturer m = inv.getArgument(0);
            m.setId(5L);
            return m;
        });

        ManufacturerDto result = service.create(dto(null, "  Sony "));

        ArgumentCaptor<Manufacturer> captor = ArgumentCaptor.forClass(Manufacturer.class);
        verify(manufacturerRepository).save(captor.capture());
        assertEquals("Sony", captor.getValue().getName());
        assertEquals(5L, result.getId());
        assertEquals("Sony", result.getName());
    }

    @Test
    void create_duplicateName_throws() {
        when(manufacturerRepository.existsByNameIgnoreCase("Sony")).thenReturn(true);

        assertThrows(ManufacturerAlreadyExistsException.class, () -> service.create(dto(null, "Sony")));
        verify(manufacturerRepository, never()).save(any());
    }

    @Test
    void update_changesName() {
        Manufacturer existing = manufacturer(1L, "Old");
        when(manufacturerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(manufacturerRepository.existsByNameIgnoreCaseAndIdNot("New", 1L)).thenReturn(false);
        when(manufacturerRepository.save(existing)).thenReturn(existing);

        ManufacturerDto result = service.update(1L, dto(1L, "New"));

        assertEquals("New", existing.getName());
        assertEquals("New", result.getName());
    }

    @Test
    void update_nameTakenByAnother_throws() {
        when(manufacturerRepository.findById(1L)).thenReturn(Optional.of(manufacturer(1L, "Old")));
        when(manufacturerRepository.existsByNameIgnoreCaseAndIdNot("Taken", 1L)).thenReturn(true);

        assertThrows(ManufacturerAlreadyExistsException.class, () -> service.update(1L, dto(1L, "Taken")));
        verify(manufacturerRepository, never()).save(any());
    }

    @Test
    void update_notFound_throws() {
        when(manufacturerRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update(9L, dto(9L, "X")));
    }

    @Test
    void delete_removesManufacturer() {
        Manufacturer existing = manufacturer(1L, "Sony");
        when(manufacturerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(applianceRepository.existsByManufacturerId(1L)).thenReturn(false);

        service.delete(1L);

        verify(manufacturerRepository).delete(existing);
    }

    @Test
    void delete_usedByAppliances_throws() {
        when(manufacturerRepository.findById(1L)).thenReturn(Optional.of(manufacturer(1L, "Sony")));
        when(applianceRepository.existsByManufacturerId(1L)).thenReturn(true);

        assertThrows(ResourceInUseException.class, () -> service.delete(1L));
        verify(manufacturerRepository, never()).delete(any());
    }

    @Test
    void delete_notFound_throws() {
        when(manufacturerRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(9L));
    }
}

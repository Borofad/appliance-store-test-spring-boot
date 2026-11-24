package com.epam.rd.autocode.assessment.appliances.service.impl;

import com.epam.rd.autocode.assessment.appliances.config.MapperConfig;
import com.epam.rd.autocode.assessment.appliances.dto.ApplianceDto;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceInUseException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceNotFoundException;
import com.epam.rd.autocode.assessment.appliances.model.Appliance;
import com.epam.rd.autocode.assessment.appliances.model.Category;
import com.epam.rd.autocode.assessment.appliances.model.Manufacturer;
import com.epam.rd.autocode.assessment.appliances.model.PowerType;
import com.epam.rd.autocode.assessment.appliances.repository.ApplianceInOrderRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplianceServiceImplTest {

    @Mock
    private ApplianceRepository applianceRepository;
    @Mock
    private ManufacturerRepository manufacturerRepository;
    @Mock
    private ApplianceInOrderRepository applianceInOrderRepository;

    private ApplianceServiceImpl service;

    private Manufacturer samsung;

    @BeforeEach
    void setUp() {
        service = new ApplianceServiceImpl(applianceRepository, manufacturerRepository,
                applianceInOrderRepository, new MapperConfig().modelMapper());
        samsung = new Manufacturer(5L, "Samsung");
    }

    private Appliance appliance(Long id, String name) {
        Appliance a = new Appliance();
        a.setId(id);
        a.setName(name);
        a.setCategory(Category.BIG);
        a.setModel("M-1");
        a.setManufacturer(samsung);
        a.setPowerType(PowerType.AC220);
        a.setCharacteristic("quiet");
        a.setDescription("desc");
        a.setPower(1500);
        a.setPrice(new BigDecimal("99.90"));
        return a;
    }

    private ApplianceDto dto(Long manufacturerId) {
        ApplianceDto d = new ApplianceDto();
        d.setName("  Fridge ");
        d.setCategory(Category.SMALL);
        d.setModel(" X-100 ");
        d.setManufacturerId(manufacturerId);
        d.setPowerType(PowerType.AC110);
        d.setCharacteristic("cold");
        d.setDescription("A fridge");
        d.setPower(300);
        d.setPrice(new BigDecimal("250.00"));
        return d;
    }

    @Test
    void search_nullQuery_usesEmptyTextAndMapsManufacturer() {
        Pageable pageable = PageRequest.of(0, 5);
        when(applianceRepository.search("", pageable))
                .thenReturn(new PageImpl<>(List.of(appliance(1L, "Oven"))));

        Page<ApplianceDto> result = service.search(null, pageable);

        assertEquals(1, result.getContent().size());
        ApplianceDto first = result.getContent().get(0);
        assertEquals("Oven", first.getName());
        assertEquals(5L, first.getManufacturerId());
        assertEquals("Samsung", first.getManufacturerName());
    }

    @Test
    void findById_found() {
        when(applianceRepository.findById(1L)).thenReturn(Optional.of(appliance(1L, "Oven")));

        ApplianceDto result = service.findById(1L);

        assertEquals("Oven", result.getName());
        assertEquals(new BigDecimal("99.90"), result.getPrice());
    }

    @Test
    void findById_notFound_throws() {
        when(applianceRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findById(9L));
    }

    @Test
    void create_loadsManufacturerAndTrimsText() {
        when(manufacturerRepository.findById(5L)).thenReturn(Optional.of(samsung));
        when(applianceRepository.save(any(Appliance.class))).thenAnswer(inv -> {
            Appliance a = inv.getArgument(0);
            a.setId(10L);
            return a;
        });

        ApplianceDto result = service.create(dto(5L));

        ArgumentCaptor<Appliance> captor = ArgumentCaptor.forClass(Appliance.class);
        verify(applianceRepository).save(captor.capture());
        Appliance saved = captor.getValue();
        assertSame(samsung, saved.getManufacturer());
        assertEquals("Fridge", saved.getName());
        assertEquals("X-100", saved.getModel());
        assertEquals(Category.SMALL, saved.getCategory());
        assertEquals(PowerType.AC110, saved.getPowerType());
        assertEquals(300, saved.getPower());
        assertEquals(10L, result.getId());
        assertEquals("Samsung", result.getManufacturerName());
    }

    @Test
    void create_unknownManufacturer_throws() {
        when(manufacturerRepository.findById(77L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.create(dto(77L)));
        verify(applianceRepository, never()).save(any());
    }

    @Test
    void update_changesFields() {
        Appliance existing = appliance(1L, "Old name");
        when(applianceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(manufacturerRepository.findById(5L)).thenReturn(Optional.of(samsung));
        when(applianceRepository.save(existing)).thenReturn(existing);

        ApplianceDto result = service.update(1L, dto(5L));

        assertEquals("Fridge", existing.getName());
        assertEquals(new BigDecimal("250.00"), existing.getPrice());
        assertEquals("Fridge", result.getName());
    }

    @Test
    void update_notFound_throws() {
        when(applianceRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update(9L, dto(5L)));
    }

    @Test
    void delete_removesAppliance() {
        Appliance existing = appliance(1L, "Oven");
        when(applianceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(applianceInOrderRepository.existsByApplianceId(1L)).thenReturn(false);

        service.delete(1L);

        verify(applianceRepository).delete(existing);
    }

    @Test
    void delete_usedInOrder_throws() {
        when(applianceRepository.findById(1L)).thenReturn(Optional.of(appliance(1L, "Oven")));
        when(applianceInOrderRepository.existsByApplianceId(1L)).thenReturn(true);

        assertThrows(ResourceInUseException.class, () -> service.delete(1L));
        verify(applianceRepository, never()).delete(any());
    }

    @Test
    void delete_notFound_throws() {
        when(applianceRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(9L));
    }
}

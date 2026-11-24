package com.epam.rd.autocode.assessment.appliances.service.impl;

import com.epam.rd.autocode.assessment.appliances.config.MapperConfig;
import com.epam.rd.autocode.assessment.appliances.dto.OrderDto;
import com.epam.rd.autocode.assessment.appliances.dto.OrderRowDto;
import com.epam.rd.autocode.assessment.appliances.exception.EmptyOrderException;
import com.epam.rd.autocode.assessment.appliances.exception.OrderAlreadyApprovedException;
import com.epam.rd.autocode.assessment.appliances.exception.ResourceNotFoundException;
import com.epam.rd.autocode.assessment.appliances.model.Appliance;
import com.epam.rd.autocode.assessment.appliances.model.Client;
import com.epam.rd.autocode.assessment.appliances.model.Employee;
import com.epam.rd.autocode.assessment.appliances.model.OrderRow;
import com.epam.rd.autocode.assessment.appliances.model.Orders;
import com.epam.rd.autocode.assessment.appliances.repository.ApplianceRepository;
import com.epam.rd.autocode.assessment.appliances.repository.ClientRepository;
import com.epam.rd.autocode.assessment.appliances.repository.EmployeeRepository;
import com.epam.rd.autocode.assessment.appliances.repository.OrdersRepository;
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
class OrderServiceImplTest {

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private ApplianceRepository applianceRepository;

    private OrderServiceImpl service;

    private Client client;
    private Appliance fridge;   // 100.00
    private Appliance kettle;   // 19.99

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(ordersRepository, clientRepository, employeeRepository,
                applianceRepository, new MapperConfig().modelMapper());
        client = new Client(1L, "Anna", "anna@x.com", "HASH", "5326-0001");
        fridge = appliance(10L, "Fridge", "100.00");
        kettle = appliance(11L, "Kettle", "19.99");
    }

    private Appliance appliance(Long id, String name, String price) {
        Appliance a = new Appliance();
        a.setId(id);
        a.setName(name);
        a.setPrice(new BigDecimal(price));
        return a;
    }

    private OrderRow row(Long id, Appliance appliance, long number, String amount) {
        OrderRow r = new OrderRow();
        r.setId(id);
        r.setAppliance(appliance);
        r.setNumber(number);
        r.setAmount(new BigDecimal(amount));
        return r;
    }

    private OrderRowDto rowDto(Long applianceId, Long number) {
        OrderRowDto r = new OrderRowDto();
        r.setApplianceId(applianceId);
        r.setNumber(number);
        r.setAmount(new BigDecimal("0.01")); // must be ignored by the service
        return r;
    }

    private Orders order(Long id, boolean approved, OrderRow... rows) {
        Orders o = new Orders();
        o.setId(id);
        o.setClient(client);
        o.setApproved(approved);
        for (OrderRow r : rows) {
            o.getOrderRowSet().add(r);
        }
        return o;
    }

    private OrderDto dto(Long clientId, OrderRowDto... rows) {
        OrderDto d = new OrderDto();
        d.setClientId(clientId);
        d.setRows(new java.util.ArrayList<>(List.of(rows)));
        return d;
    }

    // ---------- reading ----------

    @Test
    void findAll_mapsOrders() {
        Pageable pageable = PageRequest.of(0, 10);
        when(ordersRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(order(1L, false, row(1L, fridge, 1, "100.00")))));

        Page<OrderDto> result = service.findAll(pageable);

        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getContent().get(0).getClientId());
        assertEquals("Anna", result.getContent().get(0).getClientName());
    }

    @Test
    void findByClientEmail_mapsOrders() {
        Pageable pageable = PageRequest.of(0, 10);
        when(ordersRepository.findByClientEmail("anna@x.com", pageable))
                .thenReturn(new PageImpl<>(List.of(order(1L, false))));

        assertEquals(1, service.findByClientEmail("anna@x.com", pageable).getContent().size());
    }

    @Test
    void findById_mapsRowsSortedByIdAndCalculatesTotal() {
        Orders o = order(1L, false, row(2L, kettle, 1, "19.99"), row(1L, fridge, 2, "200.00"));
        when(ordersRepository.findByIdWithRows(1L)).thenReturn(Optional.of(o));

        OrderDto result = service.findById(1L);

        assertEquals(2, result.getRows().size());
        assertEquals(1L, result.getRows().get(0).getId());
        assertEquals(10L, result.getRows().get(0).getApplianceId());
        assertEquals("Fridge", result.getRows().get(0).getApplianceName());
        assertEquals(2L, result.getRows().get(1).getId());
        assertEquals(0, new BigDecimal("219.99").compareTo(result.getTotal()));
        assertNull(result.getEmployeeId());
        assertFalse(result.getApproved());
    }

    @Test
    void findById_notFound_throws() {
        when(ordersRepository.findByIdWithRows(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findById(9L));
    }

    @Test
    void isOwner_delegatesToRepository() {
        when(ordersRepository.existsByIdAndClientEmail(1L, "anna@x.com")).thenReturn(true);
        when(ordersRepository.existsByIdAndClientEmail(1L, "other@x.com")).thenReturn(false);

        assertTrue(service.isOwner(1L, "anna@x.com"));
        assertFalse(service.isOwner(1L, "other@x.com"));
    }

    // ---------- create ----------

    @Test
    void create_mergesSameApplianceAndCalculatesAmounts() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(applianceRepository.findById(10L)).thenReturn(Optional.of(fridge));
        when(applianceRepository.findById(11L)).thenReturn(Optional.of(kettle));
        when(ordersRepository.save(any(Orders.class))).thenAnswer(inv -> {
            Orders o = inv.getArgument(0);
            o.setId(77L);
            return o;
        });

        OrderDto result = service.create(dto(1L, rowDto(10L, 2L), rowDto(10L, 3L), rowDto(11L, 1L)));

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(ordersRepository).save(captor.capture());
        assertFalse(captor.getValue().getApproved());
        assertSame(client, captor.getValue().getClient());
        assertEquals(2, captor.getValue().getOrderRowSet().size());

        assertEquals(77L, result.getId());
        assertEquals(1L, result.getClientId());
        assertEquals(2, result.getRows().size());
        OrderRowDto fridgeRow = result.getRows().stream()
                .filter(r -> r.getApplianceId().equals(10L)).findFirst().orElseThrow();
        assertEquals(5L, fridgeRow.getNumber());
        assertEquals(0, new BigDecimal("500.00").compareTo(fridgeRow.getAmount()));
        assertEquals(0, new BigDecimal("519.99").compareTo(result.getTotal()));
    }

    @Test
    void create_noClientId_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.create(dto(null, rowDto(10L, 1L))));
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void create_unknownClient_throws() {
        when(clientRepository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.create(dto(5L, rowDto(10L, 1L))));
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void create_noRows_throws() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));

        assertThrows(EmptyOrderException.class, () -> service.create(dto(1L)));
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void create_unknownAppliance_throws() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(applianceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.create(dto(1L, rowDto(99L, 1L))));
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void create_nonPositiveQuantity_throws() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));

        assertThrows(IllegalArgumentException.class, () -> service.create(dto(1L, rowDto(10L, 0L))));
        verify(ordersRepository, never()).save(any());
    }

    // ---------- update ----------

    @Test
    void update_replacesRows() {
        Orders existing = order(1L, false, row(1L, fridge, 1, "100.00"));
        when(ordersRepository.findByIdWithRows(1L)).thenReturn(Optional.of(existing));
        when(applianceRepository.findById(11L)).thenReturn(Optional.of(kettle));
        when(ordersRepository.save(existing)).thenReturn(existing);

        OrderDto result = service.update(1L, dto(1L, rowDto(11L, 2L)));

        assertEquals(1, existing.getOrderRowSet().size());
        OrderRow only = existing.getOrderRowSet().iterator().next();
        assertSame(kettle, only.getAppliance());
        assertEquals(0, new BigDecimal("39.98").compareTo(only.getAmount()));
        assertEquals(0, new BigDecimal("39.98").compareTo(result.getTotal()));
    }

    @Test
    void update_approvedOrder_throws() {
        when(ordersRepository.findByIdWithRows(1L))
                .thenReturn(Optional.of(order(1L, true, row(1L, fridge, 1, "100.00"))));

        assertThrows(OrderAlreadyApprovedException.class, () -> service.update(1L, dto(1L, rowDto(11L, 1L))));
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void update_noRows_throwsAndKeepsOldRows() {
        Orders existing = order(1L, false, row(1L, fridge, 1, "100.00"));
        when(ordersRepository.findByIdWithRows(1L)).thenReturn(Optional.of(existing));

        assertThrows(EmptyOrderException.class, () -> service.update(1L, dto(1L)));
        assertEquals(1, existing.getOrderRowSet().size());
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void update_notFound_throws() {
        when(ordersRepository.findByIdWithRows(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update(9L, dto(1L, rowDto(10L, 1L))));
    }

    // ---------- approve ----------

    @Test
    void approve_setsEmployeeAndApproved() {
        Orders existing = order(1L, false, row(1L, fridge, 1, "100.00"));
        Employee employee = new Employee(3L, "Bob", "bob@x.com", "HASH", "sales");
        when(ordersRepository.findByIdWithRows(1L)).thenReturn(Optional.of(existing));
        when(employeeRepository.findByEmail("bob@x.com")).thenReturn(Optional.of(employee));
        when(ordersRepository.save(existing)).thenReturn(existing);

        OrderDto result = service.approve(1L, "bob@x.com");

        assertTrue(existing.getApproved());
        assertSame(employee, existing.getEmployee());
        assertTrue(result.getApproved());
        assertEquals(3L, result.getEmployeeId());
        assertEquals("Bob", result.getEmployeeName());
    }

    @Test
    void approve_alreadyApproved_throws() {
        when(ordersRepository.findByIdWithRows(1L))
                .thenReturn(Optional.of(order(1L, true, row(1L, fridge, 1, "100.00"))));

        assertThrows(OrderAlreadyApprovedException.class, () -> service.approve(1L, "bob@x.com"));
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void approve_emptyOrder_throws() {
        when(ordersRepository.findByIdWithRows(1L)).thenReturn(Optional.of(order(1L, false)));

        assertThrows(EmptyOrderException.class, () -> service.approve(1L, "bob@x.com"));
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void approve_unknownEmployee_throws() {
        when(ordersRepository.findByIdWithRows(1L))
                .thenReturn(Optional.of(order(1L, false, row(1L, fridge, 1, "100.00"))));
        when(employeeRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.approve(1L, "ghost@x.com"));
        verify(ordersRepository, never()).save(any());
    }

    @Test
    void approve_orderNotFound_throws() {
        when(ordersRepository.findByIdWithRows(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.approve(9L, "bob@x.com"));
    }

    // ---------- delete ----------

    @Test
    void delete_pendingOrder_removesIt() {
        Orders existing = order(1L, false, row(1L, fridge, 1, "100.00"));
        when(ordersRepository.findByIdWithRows(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        verify(ordersRepository).delete(existing);
    }

    @Test
    void delete_approvedOrder_throws() {
        when(ordersRepository.findByIdWithRows(1L))
                .thenReturn(Optional.of(order(1L, true, row(1L, fridge, 1, "100.00"))));

        assertThrows(OrderAlreadyApprovedException.class, () -> service.delete(1L));
        verify(ordersRepository, never()).delete(any());
    }

    @Test
    void delete_notFound_throws() {
        when(ordersRepository.findByIdWithRows(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(9L));
    }
}

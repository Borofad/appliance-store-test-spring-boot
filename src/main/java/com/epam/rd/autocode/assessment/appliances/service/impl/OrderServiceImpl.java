package com.epam.rd.autocode.assessment.appliances.service.impl;

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
import com.epam.rd.autocode.assessment.appliances.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrdersRepository ordersRepository;
    private final ClientRepository clientRepository;
    private final EmployeeRepository employeeRepository;
    private final ApplianceRepository applianceRepository;
    private final ModelMapper modelMapper;

    @Override
    public Page<OrderDto> findAll(Pageable pageable) {
        return ordersRepository.findAll(pageable).map(this::toDto);
    }

    @Override
    public Page<OrderDto> findByClientEmail(String email, Pageable pageable) {
        return ordersRepository.findByClientEmail(email, pageable).map(this::toDto);
    }

    @Override
    public OrderDto findById(Long id) {
        return toDto(getEntityWithRows(id));
    }

    @Override
    @Transactional
    public OrderDto create(OrderDto dto) {
        if (dto.getClientId() == null) {
            throw new IllegalArgumentException("clientId is required to create an order");
        }
        Client client = clientRepository.findById(dto.getClientId())
                .orElseThrow(() -> ResourceNotFoundException.client(dto.getClientId()));

        Orders order = new Orders();
        order.setClient(client);
        order.setApproved(false);
        order.getOrderRowSet().addAll(buildRows(dto.getRows()));

        Orders saved = ordersRepository.save(order);
        log.info("Order created: id={}, clientId={}, rows={}", saved.getId(), client.getId(), saved.getOrderRowSet().size());
        return toDto(saved);
    }

    @Override
    @Transactional
    public OrderDto update(Long id, OrderDto dto) {
        Orders order = getEntityWithRows(id);
        if (Boolean.TRUE.equals(order.getApproved())) {
            throw new OrderAlreadyApprovedException();
        }
        List<OrderRow> newRows = buildRows(dto.getRows()); // validates before touching the order
        order.getOrderRowSet().clear();                    // orphanRemoval deletes the old rows
        order.getOrderRowSet().addAll(newRows);
        log.info("Order updated: id={}, rows={}", id, newRows.size());
        return toDto(ordersRepository.save(order));
    }

    @Override
    @Transactional
    public OrderDto approve(Long orderId, String employeeEmail) {
        Orders order = getEntityWithRows(orderId);
        if (Boolean.TRUE.equals(order.getApproved())) {
            throw new OrderAlreadyApprovedException();
        }
        if (order.getOrderRowSet().isEmpty()) {
            throw new EmptyOrderException();
        }
        Employee employee = employeeRepository.findByEmail(employeeEmail)
                .orElseThrow(() -> ResourceNotFoundException.employee(employeeEmail));
        order.setEmployee(employee);
        order.setApproved(true);
        log.info("Order approved: id={}, employeeId={}", orderId, employee.getId());
        return toDto(ordersRepository.save(order));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Orders order = getEntityWithRows(id);
        if (Boolean.TRUE.equals(order.getApproved())) {
            log.warn("Refused to delete approved order id={}", id);
            throw new OrderAlreadyApprovedException();
        }
        ordersRepository.delete(order);
        log.info("Order deleted: id={}", id);
    }

    @Override
    public boolean isOwner(Long orderId, String clientEmail) {
        return ordersRepository.existsByIdAndClientEmail(orderId, clientEmail);
    }

    /**
     * Builds order rows from the form: merges rows with the same appliance, loads each appliance from
     * the database and calculates amount = current price * quantity. Amounts from the form are ignored.
     */
    private List<OrderRow> buildRows(List<OrderRowDto> rowDtos) {
        if (rowDtos == null || rowDtos.isEmpty()) {
            throw new EmptyOrderException();
        }
        Map<Long, Long> quantities = new LinkedHashMap<>();
        for (OrderRowDto rowDto : rowDtos) {
            if (rowDto.getApplianceId() == null || rowDto.getNumber() == null || rowDto.getNumber() <= 0) {
                throw new IllegalArgumentException("Every order row needs an appliance and a positive quantity");
            }
            quantities.merge(rowDto.getApplianceId(), rowDto.getNumber(), Long::sum);
        }

        List<OrderRow> rows = new ArrayList<>();
        quantities.forEach((applianceId, number) -> {
            Appliance appliance = applianceRepository.findById(applianceId)
                    .orElseThrow(() -> ResourceNotFoundException.appliance(applianceId));
            OrderRow row = new OrderRow();
            row.setAppliance(appliance);
            row.setNumber(number);
            row.setAmount(appliance.getPrice()
                    .multiply(BigDecimal.valueOf(number))
                    .setScale(2, RoundingMode.HALF_UP));
            rows.add(row);
        });
        return rows;
    }

    private Orders getEntityWithRows(Long id) {
        return ordersRepository.findByIdWithRows(id).orElseThrow(() -> ResourceNotFoundException.order(id));
    }

    private OrderDto toDto(Orders order) {
        OrderDto dto = modelMapper.map(order, OrderDto.class);
        Set<OrderRow> rows = order.getOrderRowSet() == null ? new HashSet<>() : order.getOrderRowSet();
        List<OrderRowDto> rowDtos = new ArrayList<>();
        for (OrderRow row : rows) {
            rowDtos.add(modelMapper.map(row, OrderRowDto.class));
        }
        rowDtos.sort(Comparator.comparing(OrderRowDto::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        dto.setRows(rowDtos);
        return dto;
    }
}
package com.epam.rd.autocode.assessment.appliances.service;

import com.epam.rd.autocode.assessment.appliances.dto.OrderDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    /** All orders (employee view). */
    Page<OrderDto> findAll(Pageable pageable);

    /** The orders of one client, identified by the login name (email). */
    Page<OrderDto> findByClientEmail(String email, Pageable pageable);

    /** One order with its rows. */
    OrderDto findById(Long id);

    /**
     * Creates an order for dto.getClientId(). Row amounts are calculated from the current appliance
     * prices; rows with the same appliance are merged.
     *
     * @throws com.epam.rd.autocode.assessment.appliances.exception.EmptyOrderException if there are no rows
     */
    OrderDto create(OrderDto dto);

    /**
     * Replaces the rows of a not-yet-approved order.
     *
     * @throws com.epam.rd.autocode.assessment.appliances.exception.OrderAlreadyApprovedException if the order is already approved
     */
    OrderDto update(Long id, OrderDto dto);

    /**
     * Approves the order on behalf of the employee with the given login email.
     *
     * @throws com.epam.rd.autocode.assessment.appliances.exception.OrderAlreadyApprovedException if it is already approved
     */
    OrderDto approve(Long orderId, String employeeEmail);

    /** @throws com.epam.rd.autocode.assessment.appliances.exception.OrderAlreadyApprovedException if the order is approved */
    void delete(Long id);

    /** True if the order belongs to the client with this email (for @PreAuthorize checks). */
    boolean isOwner(Long orderId, String clientEmail);
}

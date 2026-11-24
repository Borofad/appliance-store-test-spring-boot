package com.epam.rd.autocode.assessment.appliances.dto;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Order as shown/edited in the UI. clientId is filled from the logged-in client by the service
 * (or chosen by an employee), employeeId is set when an employee approves the order.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {

    private Long id;

    private Long clientId;

    /** Display only. */
    private String clientName;

    private Long employeeId;

    /** Display only. */
    private String employeeName;

    private Boolean approved = false;

    @Valid
    private List<OrderRowDto> rows = new ArrayList<>();

    /** Sum of all row amounts (derived, not stored). */
    public BigDecimal getTotal() {
        return rows.stream()
                .map(OrderRowDto::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

package com.epam.rd.autocode.assessment.appliances.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRowDto {

    private Long id;

    @NotNull(message = "{validation.orderRow.appliance.required}")
    private Long applianceId;

    /** Display only. */
    private String applianceName;

    /** Quantity. */
    @NotNull(message = "{validation.orderRow.number.required}")
    @Positive(message = "{validation.orderRow.number.positive}")
    private Long number;

    /** Row total. Calculated by the service (price * number); never trusted from the form. */
    private BigDecimal amount;
}

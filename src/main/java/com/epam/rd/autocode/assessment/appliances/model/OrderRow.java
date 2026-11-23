package com.epam.rd.autocode.assessment.appliances.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Table(name = "order_row")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "appliance")
public class OrderRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "{validation.orderRow.appliance.required}")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appliance_id", nullable = false)
    private Appliance appliance;

    /** Quantity of the appliance in this row. */
    @NotNull(message = "{validation.orderRow.number.required}")
    @Positive(message = "{validation.orderRow.number.positive}")
    @Column(nullable = false)
    private Long number;

    /** Row total = appliance price * number. */
    @NotNull(message = "{validation.orderRow.amount.required}")
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
}
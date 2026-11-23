package com.epam.rd.autocode.assessment.appliances.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Table(name = "appliance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "manufacturer")
public class Appliance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "{validation.appliance.name.required}")
    @Size(max = 100, message = "{validation.appliance.name.size}")
    @Column(nullable = false, length = 100)
    private String name;

    @NotNull(message = "{validation.appliance.category.required}")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @NotBlank(message = "{validation.appliance.model.required}")
    @Size(max = 100, message = "{validation.appliance.model.size}")
    @Column(nullable = false, length = 100)
    private String model;

    @NotNull(message = "{validation.appliance.manufacturer.required}")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manufacturer_id", nullable = false)
    private Manufacturer manufacturer;

    @NotNull(message = "{validation.appliance.powerType.required}")
    @Enumerated(EnumType.STRING)
    @Column(name = "power_type", nullable = false)
    private PowerType powerType;

    @Size(max = 255, message = "{validation.appliance.characteristic.size}")
    private String characteristic;

    @Size(max = 1000, message = "{validation.appliance.description.size}")
    @Column(length = 1000)
    private String description;

    @NotNull(message = "{validation.appliance.power.required}")
    @Positive(message = "{validation.appliance.power.positive}")
    @Column(nullable = false)
    private Integer power;

    @NotNull(message = "{validation.appliance.price.required}")
    @DecimalMin(value = "0.01", message = "{validation.appliance.price.min}")
    @Digits(integer = 10, fraction = 2, message = "{validation.appliance.price.digits}")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
}
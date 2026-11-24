package com.epam.rd.autocode.assessment.appliances.dto;

import com.epam.rd.autocode.assessment.appliances.model.Category;
import com.epam.rd.autocode.assessment.appliances.model.PowerType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Form/list object for an appliance. The manufacturer is exposed as manufacturerId (what a form
 * select sends back) and manufacturerName (for display only).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplianceDto {

    private Long id;

    @NotBlank(message = "{validation.appliance.name.required}")
    @Size(max = 100, message = "{validation.appliance.name.size}")
    private String name;

    @NotNull(message = "{validation.appliance.category.required}")
    private Category category;

    @NotBlank(message = "{validation.appliance.model.required}")
    @Size(max = 100, message = "{validation.appliance.model.size}")
    private String model;

    @NotNull(message = "{validation.appliance.manufacturer.required}")
    private Long manufacturerId;

    /** Display only (filled when reading, ignored when saving). */
    private String manufacturerName;

    @NotNull(message = "{validation.appliance.powerType.required}")
    private PowerType powerType;

    @Size(max = 255, message = "{validation.appliance.characteristic.size}")
    private String characteristic;

    @Size(max = 1000, message = "{validation.appliance.description.size}")
    private String description;

    @NotNull(message = "{validation.appliance.power.required}")
    @Positive(message = "{validation.appliance.power.positive}")
    private Integer power;

    @NotNull(message = "{validation.appliance.price.required}")
    @DecimalMin(value = "0.01", message = "{validation.appliance.price.min}")
    @Digits(integer = 10, fraction = 2, message = "{validation.appliance.price.digits}")
    private BigDecimal price;
}

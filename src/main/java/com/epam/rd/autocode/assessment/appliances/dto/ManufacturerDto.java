package com.epam.rd.autocode.assessment.appliances.dto;

import com.epam.rd.autocode.assessment.appliances.validation.UniqueManufacturerName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@UniqueManufacturerName
public class ManufacturerDto {

    private Long id;

    @NotBlank(message = "{validation.manufacturer.name.required}")
    @Size(max = 100, message = "{validation.manufacturer.name.size}")
    private String name;
}

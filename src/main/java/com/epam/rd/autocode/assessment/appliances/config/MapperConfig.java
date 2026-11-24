package com.epam.rd.autocode.assessment.appliances.config;

import com.epam.rd.autocode.assessment.appliances.dto.*;
import com.epam.rd.autocode.assessment.appliances.model.*;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ModelMapper setup.
 *
 * Entity -> DTO is fully mapped (including names of related entities for display).
 * DTO -> entity is mapped ONLY for simple fields. Relations (manufacturer, client, employee,
 * appliance, order rows) and passwords are never mapped automatically: the services load the
 * related entities from the repositories and encode passwords themselves.
 */
@Configuration
public class MapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);

        // ---------- entity -> DTO ----------
        mapper.typeMap(Client.class, ClientDto.class)
                .addMappings(m -> m.skip(ClientDto::setPassword)); // never expose the hash

        mapper.typeMap(Employee.class, EmployeeDto.class)
                .addMappings(m -> m.skip(EmployeeDto::setPassword));

        // Appliance -> ApplianceDto, OrderRow -> OrderRowDto and Orders -> OrderDto: the ids and names of
        // related entities (manufacturerId, applianceName, clientId, employeeName, ...) are matched
        // automatically because the DTO property names follow the "<relation><Property>" pattern.
        // The rows are skipped here: OrderServiceImpl fills them in itself (mapping each OrderRow).
        mapper.typeMap(Appliance.class, ApplianceDto.class);
        mapper.typeMap(OrderRow.class, OrderRowDto.class);
        mapper.typeMap(Orders.class, OrderDto.class)
                .addMappings(m -> m.skip(OrderDto::setRows));

        // ---------- DTO -> entity (simple fields only) ----------
        mapper.typeMap(ClientDto.class, Client.class).addMappings(m -> {
            m.skip(Client::setId);
            m.skip(Client::setPassword);
        });

        mapper.typeMap(EmployeeDto.class, Employee.class).addMappings(m -> {
            m.skip(Employee::setId);
            m.skip(Employee::setPassword);
        });

        mapper.typeMap(ManufacturerDto.class, Manufacturer.class)
                .addMappings(m -> m.skip(Manufacturer::setId));

        return mapper;
    }
}

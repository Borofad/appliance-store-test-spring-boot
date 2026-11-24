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

        mapper.typeMap(Appliance.class, ApplianceDto.class).setPostConverter(ctx -> {
            Manufacturer manufacturer = ctx.getSource().getManufacturer();
            if (manufacturer != null) {
                ctx.getDestination().setManufacturerId(manufacturer.getId());
                ctx.getDestination().setManufacturerName(manufacturer.getName());
            }
            return ctx.getDestination();
        });

        mapper.typeMap(OrderRow.class, OrderRowDto.class).setPostConverter(ctx -> {
            Appliance appliance = ctx.getSource().getAppliance();
            if (appliance != null) {
                ctx.getDestination().setApplianceId(appliance.getId());
                ctx.getDestination().setApplianceName(appliance.getName());
            }
            return ctx.getDestination();
        });

        // The rows are skipped here: OrderServiceImpl fills them in itself (mapping each OrderRow).
        mapper.typeMap(Orders.class, OrderDto.class)
                .addMappings(m -> m.skip(OrderDto::setRows))
                .setPostConverter(ctx -> {
                    Client client = ctx.getSource().getClient();
                    if (client != null) {
                        ctx.getDestination().setClientId(client.getId());
                        ctx.getDestination().setClientName(client.getName());
                    }
                    Employee employee = ctx.getSource().getEmployee();
                    if (employee != null) {
                        ctx.getDestination().setEmployeeId(employee.getId());
                        ctx.getDestination().setEmployeeName(employee.getName());
                    }
                    return ctx.getDestination();
                });

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

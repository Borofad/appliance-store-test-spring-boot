package com.epam.rd.autocode.assessment.appliances.repository;

import com.epam.rd.autocode.assessment.appliances.model.Manufacturer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ManufacturerRepository extends JpaRepository<Manufacturer, Long> {

    Optional<Manufacturer> findByName(String name);

    boolean existsByNameIgnoreCase(String name);

    /** For edit forms: is the name taken by another manufacturer? */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    Page<Manufacturer> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
package com.epam.rd.autocode.assessment.appliances.repository;

import com.epam.rd.autocode.assessment.appliances.model.Appliance;
import com.epam.rd.autocode.assessment.appliances.model.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ApplianceRepository extends JpaRepository<Appliance, Long> {

    /** Used to block deleting a manufacturer that still has appliances. */
    boolean existsByManufacturerId(Long manufacturerId);

    List<Appliance> findByManufacturerId(Long manufacturerId);

    @EntityGraph(attributePaths = "manufacturer")
    Page<Appliance> findByCategory(Category category, Pageable pageable);

    /** Loads manufacturers together with appliances (avoids N+1 queries in lists). */
    @Override
    @EntityGraph(attributePaths = "manufacturer")
    Page<Appliance> findAll(Pageable pageable);

    /** Search by appliance name, model or manufacturer name (case-insensitive). */
    @EntityGraph(attributePaths = "manufacturer")
    @Query("""
            select a from Appliance a
            where lower(a.name) like lower(concat('%', :query, '%'))
               or lower(a.model) like lower(concat('%', :query, '%'))
               or lower(a.manufacturer.name) like lower(concat('%', :query, '%'))
            """)
    Page<Appliance> search(@Param("query") String query, Pageable pageable);
}
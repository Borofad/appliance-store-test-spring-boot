package com.epam.rd.autocode.assessment.appliances.repository;

import com.epam.rd.autocode.assessment.appliances.model.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    /** Case-insensitive variants (H2 compares case-sensitively, MySQL does not - keep behaviour identical). */
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /** Search by name, email or department (case-insensitive) with pagination and sorting. */
    @Query("""
            select e from Employee e
            where lower(e.name) like lower(concat('%', :query, '%'))
               or lower(e.email) like lower(concat('%', :query, '%'))
               or lower(e.department) like lower(concat('%', :query, '%'))
            """)
    Page<Employee> search(@Param("query") String query, Pageable pageable);
}
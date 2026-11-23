package com.epam.rd.autocode.assessment.appliances.repository;

import com.epam.rd.autocode.assessment.appliances.model.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    /** Search by name or email (case-insensitive) with pagination and sorting. */
    @Query("""
            select c from Client c
            where lower(c.name) like lower(concat('%', :query, '%'))
               or lower(c.email) like lower(concat('%', :query, '%'))
            """)
    Page<Client> search(@Param("query") String query, Pageable pageable);
}
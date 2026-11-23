package com.epam.rd.autocode.assessment.appliances.repository;

import com.epam.rd.autocode.assessment.appliances.model.Orders;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrdersRepository extends JpaRepository<Orders, Long> {

    /** All orders (employee view), with client and employee loaded for the list page. */
    @Override
    @EntityGraph(attributePaths = {"client", "employee"})
    Page<Orders> findAll(Pageable pageable);

    /** A client's own orders. */
    @EntityGraph(attributePaths = {"client", "employee"})
    Page<Orders> findByClientId(Long clientId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "employee"})
    Page<Orders> findByApproved(Boolean approved, Pageable pageable);

    /** One order with its rows and their appliances (for details / edit / total). */
    @Query("""
            select distinct o from Orders o
            left join fetch o.client
            left join fetch o.employee
            left join fetch o.orderRowSet r
            left join fetch r.appliance
            where o.id = :id
            """)
    Optional<Orders> findByIdWithRows(@Param("id") Long id);

    /** Used to block deleting a client who still has orders. */
    boolean existsByClientId(Long clientId);

    /** Used to block deleting an employee who approved orders. */
    boolean existsByEmployeeId(Long employeeId);
}
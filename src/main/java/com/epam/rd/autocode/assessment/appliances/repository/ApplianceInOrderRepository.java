package com.epam.rd.autocode.assessment.appliances.repository;

import com.epam.rd.autocode.assessment.appliances.model.OrderRow;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for order rows (an appliance placed in an order). */
public interface ApplianceInOrderRepository extends JpaRepository<OrderRow, Long> {

    /** Used to block deleting an appliance that is part of an existing order. */
    boolean existsByApplianceId(Long applianceId);
}
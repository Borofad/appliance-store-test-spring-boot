package com.epam.rd.autocode.assessment.appliances.exception;

/** Thrown when trying to modify or delete an order that is already approved. */
public class OrderAlreadyApprovedException extends BusinessException {

    public OrderAlreadyApprovedException() {
        super("error.order.alreadyApproved");
    }
}
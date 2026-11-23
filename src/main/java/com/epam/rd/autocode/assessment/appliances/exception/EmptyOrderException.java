package com.epam.rd.autocode.assessment.appliances.exception;

/** Thrown when an order is submitted without any items. */
public class EmptyOrderException extends BusinessException {

    public EmptyOrderException() {
        super("error.order.empty");
    }
}
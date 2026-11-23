package com.epam.rd.autocode.assessment.appliances.exception;

/** Thrown when a manufacturer with the same name already exists. */
public class ManufacturerAlreadyExistsException extends BusinessException {

    public ManufacturerAlreadyExistsException(String name) {
        super("error.manufacturer.exists", name);
    }
}
package com.epam.rd.autocode.assessment.appliances.exception;

/** Thrown when deleting an entity that is still referenced by other data. */
public class ResourceInUseException extends BusinessException {

    private ResourceInUseException(String messageKey) {
        super(messageKey);
    }

    public static ResourceInUseException manufacturer() {
        return new ResourceInUseException("error.manufacturer.inUse");
    }

    public static ResourceInUseException appliance() {
        return new ResourceInUseException("error.appliance.inUse");
    }

    public static ResourceInUseException client() {
        return new ResourceInUseException("error.client.inUse");
    }

    public static ResourceInUseException employee() {
        return new ResourceInUseException("error.employee.inUse");
    }
}
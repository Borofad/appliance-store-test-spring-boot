package com.epam.rd.autocode.assessment.appliances.exception;

import org.springframework.context.support.DefaultMessageSourceResolvable;

/**
 * Thrown when an entity with the given id does not exist. Maps to HTTP 404.
 *
 * The entity name is passed as a message-key resolvable, so Spring translates it
 * into the current interface language automatically when the final message is built.
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String entityKey, Object id) {
        super("error.entity.notFound",
                new DefaultMessageSourceResolvable(new String[]{entityKey}, entityKey),
                String.valueOf(id)); // String, so MessageFormat doesn't print 1000 as "1,000"
    }

    public static ResourceNotFoundException appliance(Object id) {
        return new ResourceNotFoundException("entity.appliance", id);
    }

    public static ResourceNotFoundException manufacturer(Object id) {
        return new ResourceNotFoundException("entity.manufacturer", id);
    }

    public static ResourceNotFoundException client(Object id) {
        return new ResourceNotFoundException("entity.client", id);
    }

    public static ResourceNotFoundException employee(Object id) {
        return new ResourceNotFoundException("entity.employee", id);
    }

    public static ResourceNotFoundException order(Object id) {
        return new ResourceNotFoundException("entity.order", id);
    }

    public static ResourceNotFoundException user(Object id) {
        return new ResourceNotFoundException("entity.user", id);
    }
}
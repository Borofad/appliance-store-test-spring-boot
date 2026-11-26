package com.epam.rd.autocode.assessment.appliances.aspect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method (or every method of a class) to be logged at INFO level by {@link LoggingServices}:
 * entry with arguments, exit with result and duration, and any exception.
 *
 * Methods of the service layer are logged at DEBUG automatically, without this annotation.
 * Adding @Loggable raises that to INFO for an important business operation.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Loggable {

    /**
     * Set to false for methods that receive or return sensitive values (passwords, tokens):
     * the call is still logged, but arguments, result and exception text are replaced by a placeholder.
     */
    boolean logArguments() default true;
}
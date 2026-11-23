package com.epam.rd.autocode.assessment.appliances.exception;

/**
 * Base class for all application (business) exceptions.
 * It carries a message KEY and optional arguments instead of a ready-made text,
 * so the global exception handler can translate it into the user's language
 * using the messages bundles.
 */
public class BusinessException extends RuntimeException {

    private final String messageKey;
    private final transient Object[] args;

    public BusinessException(String messageKey, Object... args) {
        super(messageKey); // the key is what gets logged
        this.messageKey = messageKey;
        this.args = args;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArgs() {
        return args;
    }
}
package com.epam.rd.autocode.assessment.appliances.exception;

/** Thrown when registering/saving a user whose email is already taken. */
public class EmailAlreadyExistsException extends BusinessException {

    public EmailAlreadyExistsException(String email) {
        super("error.user.emailExists", email);
    }
}
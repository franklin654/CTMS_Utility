package com.ctms.ctms_backend.user.exception;

public class DuplicateUserException extends RuntimeException {

    public DuplicateUserException(String field, String value) {
        super("A user with " + field + " '" + value + "' already exists");
    }
}

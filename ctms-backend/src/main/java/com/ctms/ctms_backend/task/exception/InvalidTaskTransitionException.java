package com.ctms.ctms_backend.task.exception;

public class InvalidTaskTransitionException extends RuntimeException {

    public InvalidTaskTransitionException(String message) {
        super(message);
    }
}

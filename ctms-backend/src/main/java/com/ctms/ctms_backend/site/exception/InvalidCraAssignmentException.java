package com.ctms.ctms_backend.site.exception;

public class InvalidCraAssignmentException extends RuntimeException {
    public InvalidCraAssignmentException(String username) {
        super("User is not a CRA_MONITOR: " + username);
    }
}

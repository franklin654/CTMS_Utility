package com.ctms.ctms_backend.patientportal.exception;

public class NoLinkedSubjectException extends RuntimeException {
    public NoLinkedSubjectException(String username) {
        super("User " + username + " has no linked subject record");
    }
}

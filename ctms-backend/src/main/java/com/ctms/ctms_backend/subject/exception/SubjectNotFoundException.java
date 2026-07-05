package com.ctms.ctms_backend.subject.exception;

public class SubjectNotFoundException extends RuntimeException {
    public SubjectNotFoundException(Long id) {
        super("Subject not found: " + id);
    }
}

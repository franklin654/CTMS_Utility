package com.ctms.ctms_backend.visit.exception;

public class VisitNotFoundException extends RuntimeException {

    public VisitNotFoundException(Long id) {
        super("Visit not found: " + id);
    }
}

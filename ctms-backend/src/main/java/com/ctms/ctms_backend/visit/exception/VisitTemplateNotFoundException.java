package com.ctms.ctms_backend.visit.exception;

public class VisitTemplateNotFoundException extends RuntimeException {

    public VisitTemplateNotFoundException(Long id) {
        super("Visit template not found: " + id);
    }
}

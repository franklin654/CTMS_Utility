package com.ctms.ctms_backend.visit.exception;

public class CrossStudyDependencyException extends RuntimeException {

    public CrossStudyDependencyException(Long dependsOnTemplateId) {
        super("Visit template " + dependsOnTemplateId + " belongs to a different study and cannot be a dependency here");
    }
}

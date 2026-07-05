package com.ctms.ctms_backend.visit.exception;

public class VisitTemplateDependencyCycleException extends RuntimeException {

    public VisitTemplateDependencyCycleException(Long templateId) {
        super("Setting this dependency would create a cycle involving visit template " + templateId);
    }
}

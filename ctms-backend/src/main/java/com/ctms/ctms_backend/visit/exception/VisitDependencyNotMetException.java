package com.ctms.ctms_backend.visit.exception;

public class VisitDependencyNotMetException extends RuntimeException {

    public VisitDependencyNotMetException(String prerequisiteVisitName) {
        super("Cannot complete this visit until its prerequisite (\"" + prerequisiteVisitName + "\") is completed");
    }
}

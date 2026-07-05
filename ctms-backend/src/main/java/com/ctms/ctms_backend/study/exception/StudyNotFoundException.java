package com.ctms.ctms_backend.study.exception;

public class StudyNotFoundException extends RuntimeException {
    public StudyNotFoundException(Long id) {
        super("Study not found: " + id);
    }
}

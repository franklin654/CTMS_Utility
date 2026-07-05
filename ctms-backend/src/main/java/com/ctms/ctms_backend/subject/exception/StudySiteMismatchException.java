package com.ctms.ctms_backend.subject.exception;

public class StudySiteMismatchException extends RuntimeException {
    public StudySiteMismatchException(Long studyId, Long siteId) {
        super("Site " + siteId + " does not belong to study " + studyId);
    }
}

package com.ctms.ctms_backend.testresult.exception;

public class VisitSubjectMismatchException extends RuntimeException {

    public VisitSubjectMismatchException(Long visitId, Long subjectId) {
        super("Visit " + visitId + " does not belong to subject " + subjectId);
    }
}

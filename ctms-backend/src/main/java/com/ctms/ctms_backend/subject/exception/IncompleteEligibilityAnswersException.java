package com.ctms.ctms_backend.subject.exception;

public class IncompleteEligibilityAnswersException extends RuntimeException {
    public IncompleteEligibilityAnswersException(String criterionLabel) {
        super("Missing eligibility answer for criterion: " + criterionLabel);
    }
}

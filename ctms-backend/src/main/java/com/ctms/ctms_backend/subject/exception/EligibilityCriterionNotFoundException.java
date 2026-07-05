package com.ctms.ctms_backend.subject.exception;

public class EligibilityCriterionNotFoundException extends RuntimeException {
    public EligibilityCriterionNotFoundException(Long id) {
        super("Eligibility criterion not found: " + id);
    }
}

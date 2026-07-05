package com.ctms.ctms_backend.budget.exception;

public class BudgetNotFoundException extends RuntimeException {

    public BudgetNotFoundException(Long studyId) {
        super("No budget exists for study: " + studyId);
    }
}

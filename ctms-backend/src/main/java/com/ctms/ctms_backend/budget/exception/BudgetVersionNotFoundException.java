package com.ctms.ctms_backend.budget.exception;

public class BudgetVersionNotFoundException extends RuntimeException {

    public BudgetVersionNotFoundException(Long studyId, int versionNumber) {
        super("Budget version " + versionNumber + " not found for study " + studyId);
    }
}

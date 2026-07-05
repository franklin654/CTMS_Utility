package com.ctms.ctms_backend.budget.exception;

public class DuplicateBudgetException extends RuntimeException {

    public DuplicateBudgetException(Long studyId) {
        super("Study " + studyId + " already has a budget");
    }
}

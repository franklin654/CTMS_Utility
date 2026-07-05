package com.ctms.ctms_backend.budget.exception;

public class MissingBudgetVersionReasonException extends RuntimeException {

    public MissingBudgetVersionReasonException() {
        super("A reason is required when creating a new budget version");
    }
}

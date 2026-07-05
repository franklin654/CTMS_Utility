package com.ctms.ctms_backend.budget.entity;

/** Mirrors DocumentVersionStatus's simpler sibling -- no review/approval sub-workflow, the BRD
 * doesn't describe one for budgets. */
public enum BudgetVersionStatus {
    CURRENT,
    SUPERSEDED
}

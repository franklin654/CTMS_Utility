package com.ctms.ctms_backend.budget.entity;

/** Fixed clinical-trial financial taxonomy -- mirrors the precedent set by
 * AdverseEventSeverity/MonitoringVisitType. Categorical field, not the kind of hardcoded workflow
 * threshold/branch CLAUDE.md S2.7 targets. */
public enum CostCategory {
    INVESTIGATOR_FEES,
    MONITORING,
    SITE_PAYMENTS,
    LABS,
    OTHER
}

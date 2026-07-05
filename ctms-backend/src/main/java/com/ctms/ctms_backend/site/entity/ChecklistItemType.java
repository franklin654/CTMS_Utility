package com.ctms.ctms_backend.site.entity;

/** The 5 fixed activation prerequisites named in BRD Epic 2 Story 02 -- fixed cardinality,
 * not config-driven (contrast with Phase 2's DocumentWorkflowRole/CategoryAccessRule tables,
 * which genuinely vary per document category). */
public enum ChecklistItemType {
    FEASIBILITY_COMPLETION,
    IRB_EC_APPROVAL,
    CONTRACT_COMPLETION,
    ESSENTIAL_DOCUMENTS_SUBMISSION,
    SITE_INITIATION_VISIT
}

package com.ctms.ctms_backend.subject.entity;

/** Subject lifecycle: SCREENED -> ENROLLED -> IN_TREATMENT -> COMPLETED (linear forward
 * progression), with WITHDRAWN reachable from any non-terminal state via the dedicated
 * withdraw action (not the generic transition endpoint) -- mirrors Study's closeout-vs-transition
 * split. COMPLETED and WITHDRAWN are both terminal. */
public enum SubjectStatus {
    SCREENED,
    ENROLLED,
    IN_TREATMENT,
    COMPLETED,
    WITHDRAWN
}

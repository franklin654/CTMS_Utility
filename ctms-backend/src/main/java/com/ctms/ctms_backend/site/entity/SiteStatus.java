package com.ctms.ctms_backend.site.entity;

/** Site lifecycle: PENDING_ACTIVATION -> ACTIVE only, driven automatically by activation
 * checklist completion (Epic 2 Story 02/03) -- there is no manual transition endpoint. */
public enum SiteStatus {
    PENDING_ACTIVATION,
    ACTIVE
}

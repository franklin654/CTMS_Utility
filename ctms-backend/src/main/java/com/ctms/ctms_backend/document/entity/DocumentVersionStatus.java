package com.ctms.ctms_backend.document.entity;

/** Document version lifecycle: DRAFT -> PENDING_REVIEW -> PENDING_APPROVAL -> CURRENT, with
 * REJECTED reachable from either review stage (terminal -- the uploader must create a new
 * version, never resurrect a rejected one). CURRENT -> ARCHIVED only as a side effect of a
 * later version reaching CURRENT. */
public enum DocumentVersionStatus {
    DRAFT,
    PENDING_REVIEW,
    PENDING_APPROVAL,
    CURRENT,
    REJECTED,
    ARCHIVED
}

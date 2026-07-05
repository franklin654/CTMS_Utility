package com.ctms.ctms_backend.study.entity;

/** Study lifecycle: strictly sequential, no skipping, no going backward. DRAFT -> ACTIVE -> CONDUCT -> CLOSEOUT. */
public enum StudyStatus {
    DRAFT,
    ACTIVE,
    CONDUCT,
    CLOSEOUT
}

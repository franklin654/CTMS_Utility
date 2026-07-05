-- Phase 1: Study Management (Epic 1, Stories 01-04)

CREATE TABLE study (
    id                 BIGSERIAL PRIMARY KEY,
    study_code         VARCHAR(30),
    name               VARCHAR(255)  NOT NULL,
    protocol_id        VARCHAR(100)  NOT NULL,
    protocol_version   VARCHAR(50)   NOT NULL,
    phase              VARCHAR(30)   NOT NULL,
    sponsor            VARCHAR(255)  NOT NULL,
    status             VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
    planned_start_date DATE,
    planned_end_date   DATE,
    actual_start_date  DATE,
    actual_end_date    DATE,
    description        VARCHAR(2000),
    created_by         BIGINT        NOT NULL REFERENCES users(id),
    created_at         TIMESTAMP     NOT NULL DEFAULT now(),
    modified_by        BIGINT        NOT NULL REFERENCES users(id),
    modified_at        TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_study_code ON study(study_code);
CREATE UNIQUE INDEX uq_study_protocol_id ON study(protocol_id);

-- Study-specific queryable transition history (in addition to the generic audit_log entry
-- written via AuditService for every transition). esignature_id is populated only for the
-- CONDUCT -> CLOSEOUT transition, which requires password re-authentication (Phase 0's
-- ESignature primitive) per CLAUDE.md's "closeout-type transitions" rule.
CREATE TABLE study_status_history (
    id             BIGSERIAL PRIMARY KEY,
    study_id       BIGINT       NOT NULL REFERENCES study(id),
    from_status    VARCHAR(30),
    to_status      VARCHAR(30)  NOT NULL,
    justification  VARCHAR(2000) NOT NULL,
    changed_by     BIGINT       NOT NULL REFERENCES users(id),
    changed_at     TIMESTAMP    NOT NULL DEFAULT now(),
    esignature_id  BIGINT       REFERENCES e_signature(id)
);
CREATE INDEX idx_study_status_history_study ON study_status_history(study_id, changed_at DESC);

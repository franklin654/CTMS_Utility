-- Phase 2: Document Management & Compliance (Epic 7, Stories 01-05)

ALTER TABLE document ADD COLUMN study_id BIGINT REFERENCES study(id);
CREATE INDEX idx_document_study ON document(study_id);
CREATE INDEX idx_document_version_status ON document_version(status);

-- One row per reviewer/approver action -- append-only, mirrors study_status_history.
CREATE TABLE document_review (
    id                   BIGSERIAL PRIMARY KEY,
    document_version_id  BIGINT       NOT NULL REFERENCES document_version(id),
    stage                VARCHAR(20)  NOT NULL,   -- REVIEW | APPROVAL
    action               VARCHAR(20)  NOT NULL,   -- SUBMITTED | APPROVED | REJECTED | CHANGES_REQUESTED
    comment              VARCHAR(2000),           -- mandatory (app-layer) for REJECTED/CHANGES_REQUESTED
    acted_by             BIGINT       NOT NULL REFERENCES users(id),
    acted_at             TIMESTAMP    NOT NULL DEFAULT now(),
    esignature_id        BIGINT       REFERENCES e_signature(id)  -- populated only for APPROVAL/APPROVED
);
CREATE INDEX idx_document_review_version ON document_review(document_version_id, acted_at DESC);

-- Data-driven category -> role DENY rules (default-allow, explicit deny-list; CLAUDE.md 2.7).
CREATE TABLE document_category_access_rule (
    id          BIGSERIAL PRIMARY KEY,
    category    VARCHAR(100) NOT NULL,
    role_code   VARCHAR(64)  NOT NULL REFERENCES roles(code),
    access      VARCHAR(20)  NOT NULL DEFAULT 'DENY',
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (category, role_code)
);
INSERT INTO document_category_access_rule (category, role_code, access) VALUES
    ('PRINCIPAL_INVESTIGATOR_CV', 'PATIENT_SUBJECT', 'DENY'),
    ('FINANCIAL', 'CRA_MONITOR', 'DENY'),
    ('FINANCIAL', 'PATIENT_SUBJECT', 'DENY');

-- Data-driven reviewer/approver role per (optional) category -- category NULL = default rule.
CREATE TABLE document_workflow_role (
    id         BIGSERIAL PRIMARY KEY,
    category   VARCHAR(100),
    stage      VARCHAR(20) NOT NULL,  -- REVIEW | APPROVAL
    role_code  VARCHAR(64) NOT NULL REFERENCES roles(code)
);
INSERT INTO document_workflow_role (category, stage, role_code) VALUES
    (NULL, 'REVIEW',   'STUDY_MANAGER'),
    (NULL, 'APPROVAL', 'QA_COMPLIANCE_AUDITOR');

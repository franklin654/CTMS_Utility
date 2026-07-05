-- Phase 0: Platform Foundation
-- Cross-cutting tables: users/roles/auth, audit log, e-signature, notifications, documents, rules engine skeleton.

-- ============================================================
-- Users, roles, auth
-- ============================================================
CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE users (
    id                     BIGSERIAL PRIMARY KEY,
    username               VARCHAR(100)  NOT NULL UNIQUE,
    email                  VARCHAR(255)  NOT NULL UNIQUE,
    password_hash          VARCHAR(255)  NOT NULL,
    full_name              VARCHAR(255)  NOT NULL,
    enabled                BOOLEAN       NOT NULL DEFAULT TRUE,
    account_locked         BOOLEAN       NOT NULL DEFAULT FALSE,
    failed_login_attempts  INT           NOT NULL DEFAULT 0,
    locked_at              TIMESTAMP,
    password_changed_at    TIMESTAMP     NOT NULL DEFAULT now(),
    password_expires_at    TIMESTAMP,
    created_at             TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP     NOT NULL DEFAULT now(),
    version                BIGINT        NOT NULL DEFAULT 0
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id),
    role_id BIGINT NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

-- Enforces password-history policy (cannot reuse the last N passwords).
CREATE TABLE password_history (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id),
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_history_user ON password_history(user_id, created_at DESC);

-- Hashed refresh tokens backing JWT refresh; revocable independent of access-token expiry.
CREATE TABLE refresh_token (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_token_user ON refresh_token(user_id);

-- ============================================================
-- Audit log (generic, immutable, exportable)
-- ============================================================
CREATE TABLE audit_log (
    id            BIGSERIAL PRIMARY KEY,
    entity_name   VARCHAR(255) NOT NULL,
    entity_id     VARCHAR(100) NOT NULL,
    action        VARCHAR(50)  NOT NULL,
    performed_by  BIGINT REFERENCES users(id),
    performed_at  TIMESTAMP    NOT NULL DEFAULT now(),
    before_value  TEXT,
    after_value   TEXT,
    reason        VARCHAR(1000)
);
CREATE INDEX idx_audit_log_entity ON audit_log(entity_name, entity_id);
CREATE INDEX idx_audit_log_performed_at ON audit_log(performed_at);

-- ============================================================
-- E-signature primitive (21 CFR Part 11 groundwork)
-- ============================================================
CREATE TABLE e_signature (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    entity_name VARCHAR(255) NOT NULL,
    entity_id   VARCHAR(100) NOT NULL,
    reason      VARCHAR(1000) NOT NULL,
    signed_at   TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_e_signature_entity ON e_signature(entity_name, entity_id);

-- ============================================================
-- Notifications (in-app + email only, event-driven)
-- ============================================================
CREATE TABLE notification (
    id                BIGSERIAL PRIMARY KEY,
    recipient_user_id BIGINT       NOT NULL REFERENCES users(id),
    type              VARCHAR(100) NOT NULL,
    title             VARCHAR(255) NOT NULL,
    body              TEXT,
    link              VARCHAR(500),
    channel           VARCHAR(20)  NOT NULL DEFAULT 'IN_APP',
    read              BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at           TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_recipient ON notification(recipient_user_id, read);

-- ============================================================
-- Document / File service (versioned, checksum, RBAC-gated at app layer)
-- ============================================================
CREATE TABLE document (
    id                  BIGSERIAL PRIMARY KEY,
    category            VARCHAR(100),
    title               VARCHAR(255) NOT NULL,
    owner_id            BIGINT REFERENCES users(id),
    current_version_id  BIGINT,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE document_version (
    id               BIGSERIAL PRIMARY KEY,
    document_id      BIGINT        NOT NULL REFERENCES document(id),
    version_number   INT           NOT NULL,
    file_name        VARCHAR(255)  NOT NULL,
    storage_path     VARCHAR(1000) NOT NULL,
    content_type     VARCHAR(150),
    size_bytes        BIGINT       NOT NULL,
    checksum_sha256  VARCHAR(64)   NOT NULL,
    effective_date   DATE,
    status           VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
    uploaded_by      BIGINT        NOT NULL REFERENCES users(id),
    uploaded_at      TIMESTAMP     NOT NULL DEFAULT now(),
    UNIQUE (document_id, version_number)
);

ALTER TABLE document
    ADD CONSTRAINT fk_document_current_version
    FOREIGN KEY (current_version_id) REFERENCES document_version(id);

-- ============================================================
-- Rules/config engine skeleton (backs Drools-driven workflow/visit/document rules)
-- ============================================================
CREATE TABLE rule_set (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255)  NOT NULL UNIQUE,
    category    VARCHAR(100)  NOT NULL,
    description VARCHAR(1000),
    active      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TABLE rule_definition (
    id           BIGSERIAL PRIMARY KEY,
    rule_set_id  BIGINT    NOT NULL REFERENCES rule_set(id),
    version      INT       NOT NULL,
    drl_content  TEXT,
    active       BOOLEAN   NOT NULL DEFAULT FALSE,
    created_by   BIGINT REFERENCES users(id),
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (rule_set_id, version)
);

-- ============================================================
-- Seed roles (BRD §4 / consolidated RBAC role list)
-- ============================================================
INSERT INTO roles (code, description) VALUES
    ('ADMIN', 'System configuration, user management, rule/workflow/document/visit config'),
    ('STUDY_MANAGER', 'Study/site/subject lifecycle, document approvals, milestone tracking'),
    ('SITE_COORDINATOR', 'Subject enrollment, visit updates, document upload at site level'),
    ('INVESTIGATOR', 'Site-level clinical oversight, consent, AE reporting'),
    ('CRA_MONITOR', 'Monitoring visit logging, site oversight, compliance follow-up'),
    ('DATA_MANAGEMENT', 'Data quality views, EDC integration touchpoints'),
    ('FINANCE_MANAGER', 'Payment rules, budget tracking, holds/releases'),
    ('QA_COMPLIANCE_AUDITOR', 'Audit trail access, traceability reports, GCP/21 CFR Part 11 validation views'),
    ('CLINICAL_LEADERSHIP', 'Portfolio dashboards, milestone/risk visibility'),
    ('EXECUTIVE', 'Real-time dashboards, cross-study analytics'),
    ('SPONSOR_CRO_LEADERSHIP', 'Reporting exports, oversight views (often read-only)'),
    ('PATIENT_SUBJECT', 'Patient Portal only - own visits, tasks, documents, notifications');

ALTER TABLE site ADD COLUMN backup_cra_id BIGINT REFERENCES users(id);

CREATE TABLE monitoring_visit (
    id                BIGSERIAL PRIMARY KEY,
    site_id           BIGINT       NOT NULL REFERENCES site(id),
    cra_id            BIGINT       NOT NULL REFERENCES users(id),
    visit_type        VARCHAR(20)  NOT NULL,
    visit_date        DATE         NOT NULL,
    findings          VARCHAR(4000),
    issues_identified VARCHAR(2000),
    checklist_notes   VARCHAR(2000),
    created_by        BIGINT       NOT NULL REFERENCES users(id),
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    modified_by       BIGINT       NOT NULL REFERENCES users(id),
    modified_at       TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_monitoring_visit_site ON monitoring_visit(site_id);

CREATE TABLE monitoring_visit_report (
    id                  BIGSERIAL PRIMARY KEY,
    monitoring_visit_id BIGINT       NOT NULL REFERENCES monitoring_visit(id),
    file_name           VARCHAR(255) NOT NULL,
    storage_path        VARCHAR(500) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    size_bytes          BIGINT       NOT NULL,
    checksum_sha256     VARCHAR(64)  NOT NULL,
    uploaded_by         BIGINT       NOT NULL REFERENCES users(id),
    uploaded_at         TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_monitoring_visit_report_visit ON monitoring_visit_report(monitoring_visit_id);

CREATE TABLE milestone (
    id             BIGSERIAL PRIMARY KEY,
    study_id       BIGINT       NOT NULL REFERENCES study(id),
    milestone_type VARCHAR(20)  NOT NULL,
    planned_date   DATE         NOT NULL,
    actual_date    DATE,
    created_by     BIGINT       NOT NULL REFERENCES users(id),
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    modified_by    BIGINT       NOT NULL REFERENCES users(id),
    modified_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_milestone_study_type UNIQUE (study_id, milestone_type)
);

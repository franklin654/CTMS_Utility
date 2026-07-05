CREATE TABLE visit_template (
    id                  BIGSERIAL PRIMARY KEY,
    study_id            BIGINT       NOT NULL REFERENCES study(id),
    name                VARCHAR(255) NOT NULL,
    sequence_number     INT          NOT NULL,
    target_day          INT          NOT NULL,
    window_early_days   INT          NOT NULL DEFAULT 0,
    window_late_days    INT          NOT NULL DEFAULT 0,
    required_procedures VARCHAR(2000),
    visit_type          VARCHAR(20)  NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT true,
    created_by          BIGINT       NOT NULL REFERENCES users(id),
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    modified_by         BIGINT       NOT NULL REFERENCES users(id),
    modified_at         TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_visit_template_study ON visit_template(study_id, active);

CREATE TABLE visit (
    id                        BIGSERIAL PRIMARY KEY,
    subject_id                BIGINT       NOT NULL REFERENCES subject(id),
    visit_template_id         BIGINT       NOT NULL REFERENCES visit_template(id),
    name                      VARCHAR(255) NOT NULL,
    sequence_number           INT          NOT NULL,
    target_day                INT          NOT NULL,
    window_early_days         INT          NOT NULL,
    window_late_days          INT          NOT NULL,
    required_procedures       VARCHAR(2000),
    visit_type                VARCHAR(20)  NOT NULL,
    scheduled_date            DATE         NOT NULL,
    status                    VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    actual_date               DATE,
    actual_time               TIME,
    notes                     VARCHAR(2000),
    reason_code               VARCHAR(2000),
    rescheduled_from_visit_id BIGINT       REFERENCES visit(id),
    completed_at              TIMESTAMP,
    created_by                BIGINT       NOT NULL REFERENCES users(id),
    created_at                TIMESTAMP    NOT NULL DEFAULT now(),
    modified_by                BIGINT      NOT NULL REFERENCES users(id),
    modified_at                TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_visit_subject ON visit(subject_id, sequence_number);
CREATE INDEX idx_visit_status_scheduled_date ON visit(status, scheduled_date);
CREATE INDEX idx_visit_template_id ON visit(visit_template_id);

ALTER TABLE visit_template ADD COLUMN depends_on_visit_template_id BIGINT REFERENCES visit_template(id);

CREATE TABLE document_requirement (
    id                BIGSERIAL PRIMARY KEY,
    study_id          BIGINT       NOT NULL REFERENCES study(id),
    study_phase       VARCHAR(20)  NOT NULL,
    document_category VARCHAR(100) NOT NULL,
    mandatory         BOOLEAN      NOT NULL DEFAULT true,
    created_by        BIGINT       NOT NULL REFERENCES users(id),
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    modified_by       BIGINT       NOT NULL REFERENCES users(id),
    modified_at       TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_document_requirement UNIQUE (study_id, study_phase, document_category)
);
CREATE INDEX idx_document_requirement_study ON document_requirement(study_id);

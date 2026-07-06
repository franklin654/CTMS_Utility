ALTER TABLE document ADD COLUMN subject_id BIGINT REFERENCES subject(id);
CREATE INDEX idx_document_subject ON document(subject_id);

ALTER TABLE subject_status_history ADD COLUMN esignature_id BIGINT REFERENCES e_signature(id);
ALTER TABLE adverse_event ADD COLUMN esignature_id BIGINT REFERENCES e_signature(id);
ALTER TABLE site ADD COLUMN esignature_id BIGINT REFERENCES e_signature(id);

CREATE TABLE protocol_deviation (
    id             BIGSERIAL PRIMARY KEY,
    subject_id     BIGINT        NOT NULL REFERENCES subject(id),
    description    VARCHAR(2000) NOT NULL,
    severity       VARCHAR(20)   NOT NULL,
    deviation_date DATE          NOT NULL,
    created_by     BIGINT        NOT NULL REFERENCES users(id),
    created_at     TIMESTAMP     NOT NULL DEFAULT now(),
    modified_by    BIGINT        NOT NULL REFERENCES users(id),
    modified_at    TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_protocol_deviation_subject ON protocol_deviation(subject_id);

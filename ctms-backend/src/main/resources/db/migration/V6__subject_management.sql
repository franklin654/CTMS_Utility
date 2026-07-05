CREATE TABLE eligibility_criterion (
    id             BIGSERIAL PRIMARY KEY,
    study_id       BIGINT       NOT NULL REFERENCES study(id),
    label          VARCHAR(500) NOT NULL,
    criterion_type VARCHAR(20)  NOT NULL,   -- INCLUSION | EXCLUSION
    active         BOOLEAN      NOT NULL DEFAULT true,
    created_at     TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_eligibility_criterion_study ON eligibility_criterion(study_id, active);

CREATE TABLE subject (
    id                    BIGSERIAL PRIMARY KEY,
    subject_code          VARCHAR(30)  UNIQUE,
    study_id              BIGINT       NOT NULL REFERENCES study(id),
    site_id               BIGINT       NOT NULL REFERENCES site(id),
    first_name            VARCHAR(255) NOT NULL,
    last_name             VARCHAR(255) NOT NULL,
    date_of_birth         DATE         NOT NULL,
    gender                VARCHAR(30),
    contact_phone         VARCHAR(50),
    contact_email         VARCHAR(255),
    address               VARCHAR(500),
    emergency_contact     VARCHAR(500),
    notes                 VARCHAR(2000),
    medical_history       VARCHAR(4000),
    screening_date        DATE         NOT NULL,
    status                VARCHAR(30)  NOT NULL DEFAULT 'SCREENED',
    created_by            BIGINT       NOT NULL REFERENCES users(id),
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    modified_by           BIGINT       NOT NULL REFERENCES users(id),
    modified_at           TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_subject_study ON subject(study_id);
CREATE INDEX idx_subject_site ON subject(site_id);

-- Append-only answer log, captured at enrollment (immutable per ALCOA).
CREATE TABLE subject_eligibility_answer (
    id             BIGSERIAL PRIMARY KEY,
    subject_id     BIGINT       NOT NULL REFERENCES subject(id),
    criterion_id   BIGINT       NOT NULL REFERENCES eligibility_criterion(id),
    met            BOOLEAN      NOT NULL,
    recorded_at    TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_subject_eligibility_answer_subject ON subject_eligibility_answer(subject_id);

-- Append-only transition log, mirrors study_status_history.
CREATE TABLE subject_status_history (
    id            BIGSERIAL PRIMARY KEY,
    subject_id    BIGINT       NOT NULL REFERENCES subject(id),
    from_status   VARCHAR(30),
    to_status     VARCHAR(30)  NOT NULL,
    reason_code   VARCHAR(2000),
    changed_by    BIGINT       NOT NULL REFERENCES users(id),
    changed_at    TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_subject_status_history_subject ON subject_status_history(subject_id, changed_at DESC);

-- Seed the shared eligibility rule set + its default DRL (Phase 0's RuleSetService/DroolsRuleEngine).
INSERT INTO rule_set (name, category, description, active, created_at, updated_at) VALUES
    ('ELIGIBILITY_DEFAULT', 'ELIGIBILITY', 'Default inclusion/exclusion eligibility evaluation', true, now(), now());

INSERT INTO rule_definition (rule_set_id, version, drl_content, active, created_at)
SELECT id, 1,
'package eligibility;
import com.ctms.ctms_backend.subject.rules.EligibilityAnswerFact;
global java.util.List results;

rule "Unmet inclusion criterion"
when
    $f : EligibilityAnswerFact(criterionType == "INCLUSION", met == false)
then
    results.add("Inclusion criterion not met: " + $f.getLabel());
end

rule "Met exclusion criterion"
when
    $f : EligibilityAnswerFact(criterionType == "EXCLUSION", met == true)
then
    results.add("Exclusion criterion met: " + $f.getLabel());
end
', true, now()
FROM rule_set WHERE name = 'ELIGIBILITY_DEFAULT';

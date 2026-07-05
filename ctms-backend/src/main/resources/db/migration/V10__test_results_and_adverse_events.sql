CREATE TABLE test_result (
    id                BIGSERIAL PRIMARY KEY,
    subject_id        BIGINT       NOT NULL REFERENCES subject(id),
    visit_id          BIGINT       NOT NULL REFERENCES visit(id),
    test_name         VARCHAR(255) NOT NULL,
    result_value      VARCHAR(255) NOT NULL,
    units             VARCHAR(50),
    reference_range   VARCHAR(255),
    abnormal          BOOLEAN      NOT NULL DEFAULT false,
    status            VARCHAR(20)  NOT NULL DEFAULT 'RECORDED',
    notes             VARCHAR(2000),
    reviewed_by       BIGINT       REFERENCES users(id),
    reviewed_at       TIMESTAMP,
    created_by        BIGINT       NOT NULL REFERENCES users(id),
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    modified_by       BIGINT       NOT NULL REFERENCES users(id),
    modified_at       TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_test_result_subject ON test_result(subject_id);
CREATE INDEX idx_test_result_visit ON test_result(visit_id);

CREATE TABLE test_result_attachment (
    id                BIGSERIAL PRIMARY KEY,
    test_result_id    BIGINT       NOT NULL REFERENCES test_result(id),
    file_name         VARCHAR(255) NOT NULL,
    storage_path      VARCHAR(500) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    checksum_sha256   VARCHAR(64)  NOT NULL,
    uploaded_by       BIGINT       NOT NULL REFERENCES users(id),
    uploaded_at       TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_test_result_attachment_result ON test_result_attachment(test_result_id);

CREATE TABLE adverse_event (
    id                BIGSERIAL PRIMARY KEY,
    subject_id        BIGINT       NOT NULL REFERENCES subject(id),
    visit_id          BIGINT       REFERENCES visit(id),
    description       VARCHAR(2000) NOT NULL,
    severity          VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    resolution_notes  VARCHAR(2000),
    resolved_at       TIMESTAMP,
    created_by        BIGINT       NOT NULL REFERENCES users(id),
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    modified_by       BIGINT       NOT NULL REFERENCES users(id),
    modified_at       TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_adverse_event_subject ON adverse_event(subject_id);
CREATE INDEX idx_adverse_event_status_severity ON adverse_event(status, severity);

-- Extend TASK_RULES_DEFAULT with a 4th rule (whole-DRL replacement, mirrors RuleSetService.addDefinition).
UPDATE rule_definition SET active = false
WHERE rule_set_id = (SELECT id FROM rule_set WHERE name = 'TASK_RULES_DEFAULT') AND active = true;

INSERT INTO rule_definition (rule_set_id, version, drl_content, active, created_at)
SELECT id, 2,
'package workflow;
import com.ctms.ctms_backend.task.rules.TaskTriggerFact;
import com.ctms.ctms_backend.task.rules.TaskRuleOutcome;
global java.util.List results;

rule "Subject enrolled task rule"
when
    $f : TaskTriggerFact(eventCode == "SUBJECT_ENROLLED")
then
    results.add(new TaskRuleOutcome(48, "SITE_COORDINATOR", "STUDY_MANAGER", "MEDIUM"));
end

rule "Site activated task rule"
when
    $f : TaskTriggerFact(eventCode == "SITE_ACTIVATED")
then
    results.add(new TaskRuleOutcome(72, "STUDY_MANAGER", "ADMIN", "MEDIUM"));
end

rule "Visit missed task rule"
when
    $f : TaskTriggerFact(eventCode == "VISIT_MISSED")
then
    results.add(new TaskRuleOutcome(24, "SITE_COORDINATOR", "STUDY_MANAGER", "HIGH"));
end

rule "Adverse event high severity task rule"
when
    $f : TaskTriggerFact(eventCode == "ADVERSE_EVENT_HIGH_SEVERITY")
then
    results.add(new TaskRuleOutcome(4, "SITE_COORDINATOR", "STUDY_MANAGER", "HIGH"));
end
', true, now()
FROM rule_set WHERE name = 'TASK_RULES_DEFAULT';

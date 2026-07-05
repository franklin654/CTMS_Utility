CREATE TABLE task (
    id                    BIGSERIAL PRIMARY KEY,
    event_code            VARCHAR(50)  NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    description           VARCHAR(2000),
    entity_name           VARCHAR(50)  NOT NULL,
    entity_id             BIGINT       NOT NULL,
    owner_id              BIGINT       NOT NULL REFERENCES users(id),
    owner_role            VARCHAR(50)  NOT NULL,
    escalation_target_id  BIGINT       NOT NULL REFERENCES users(id),
    escalation_role       VARCHAR(50)  NOT NULL,
    priority              VARCHAR(20)  NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    due_at                TIMESTAMP    NOT NULL,
    escalated             BOOLEAN      NOT NULL DEFAULT false,
    escalated_at          TIMESTAMP,
    completed_at          TIMESTAMP,
    created_by            BIGINT       NOT NULL REFERENCES users(id),
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    modified_by           BIGINT       NOT NULL REFERENCES users(id),
    modified_at           TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_task_owner_status ON task(owner_id, status);
CREATE INDEX idx_task_due_status_escalated ON task(status, escalated, due_at);

-- Seed the shared TASK_RULES_DEFAULT rule set (Phase 0's RuleSetService/DroolsRuleEngine,
-- finally exercising the WORKFLOW category seeded since Phase 0).
INSERT INTO rule_set (name, category, description, active, created_at, updated_at) VALUES
    ('TASK_RULES_DEFAULT', 'WORKFLOW', 'SLA hours / owner role / escalation role / priority per trigger event', true, now(), now());

INSERT INTO rule_definition (rule_set_id, version, drl_content, active, created_at)
SELECT id, 1,
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
', true, now()
FROM rule_set WHERE name = 'TASK_RULES_DEFAULT';

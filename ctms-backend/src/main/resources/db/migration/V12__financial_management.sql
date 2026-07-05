CREATE TABLE budget (
    id          BIGSERIAL PRIMARY KEY,
    study_id    BIGINT NOT NULL REFERENCES study(id),
    created_by  BIGINT NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    modified_by BIGINT NOT NULL REFERENCES users(id),
    modified_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_budget_study UNIQUE (study_id)
);

CREATE TABLE budget_version (
    id             BIGSERIAL PRIMARY KEY,
    budget_id      BIGINT       NOT NULL REFERENCES budget(id),
    version_number INT          NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'CURRENT',
    reason         VARCHAR(500),
    created_by     BIGINT       NOT NULL REFERENCES users(id),
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_budget_version_number UNIQUE (budget_id, version_number)
);
CREATE INDEX idx_budget_version_budget ON budget_version(budget_id);

CREATE TABLE budget_line_item (
    id                 BIGSERIAL PRIMARY KEY,
    budget_version_id BIGINT       NOT NULL REFERENCES budget_version(id),
    cost_category      VARCHAR(30)  NOT NULL,
    planned_amount     NUMERIC(14,2) NOT NULL,
    currency           VARCHAR(3)   NOT NULL,
    CONSTRAINT uq_budget_line_item_category UNIQUE (budget_version_id, cost_category)
);

CREATE TABLE payment (
    id                  BIGSERIAL PRIMARY KEY,
    study_id            BIGINT       NOT NULL REFERENCES study(id),
    site_id             BIGINT       REFERENCES site(id),
    cost_category       VARCHAR(30)  NOT NULL,
    event_code          VARCHAR(50)  NOT NULL,
    trigger_entity_name VARCHAR(50)  NOT NULL,
    trigger_entity_id   BIGINT       NOT NULL,
    base_amount         NUMERIC(14,2) NOT NULL,
    multiplier          NUMERIC(6,3) NOT NULL,
    cap_amount          NUMERIC(14,2),
    amount              NUMERIC(14,2) NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    hold_reason         VARCHAR(500),
    held_at             TIMESTAMP,
    held_by             BIGINT       REFERENCES users(id),
    release_reason      VARCHAR(500),
    released_at         TIMESTAMP,
    released_by         BIGINT       REFERENCES users(id),
    esignature_id       BIGINT       REFERENCES e_signature(id),
    created_by          BIGINT       NOT NULL REFERENCES users(id),
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    modified_by         BIGINT       NOT NULL REFERENCES users(id),
    modified_at         TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_study ON payment(study_id);
CREATE INDEX idx_payment_study_category_status ON payment(study_id, cost_category, status);

-- Seed PAYMENT_RULES_DEFAULT (Phase 0's CATEGORY_PAYMENT, unused until now).
INSERT INTO rule_set (name, category, description, active, created_at, updated_at) VALUES
    ('PAYMENT_RULES_DEFAULT', 'PAYMENT', 'Base amount / multiplier / cap / currency per trigger event', true, now(), now());

INSERT INTO rule_definition (rule_set_id, version, drl_content, active, created_at)
SELECT id, 1,
'package payment;
import com.ctms.ctms_backend.payment.rules.PaymentTriggerFact;
import com.ctms.ctms_backend.payment.rules.PaymentRuleOutcome;
global java.util.List results;

rule "Visit completed payment rule"
when
    $f : PaymentTriggerFact(eventCode == "VISIT_COMPLETED")
then
    results.add(new PaymentRuleOutcome("MONITORING", new java.math.BigDecimal("500.00"), new java.math.BigDecimal("1.0"), null, "USD"));
end

rule "Site activated payment rule"
when
    $f : PaymentTriggerFact(eventCode == "SITE_ACTIVATED")
then
    results.add(new PaymentRuleOutcome("SITE_PAYMENTS", new java.math.BigDecimal("2000.00"), new java.math.BigDecimal("1.0"), null, "USD"));
end

rule "FPI milestone payment rule"
when
    $f : PaymentTriggerFact(eventCode == "MILESTONE_REACHED_FPI")
then
    results.add(new PaymentRuleOutcome("INVESTIGATOR_FEES", new java.math.BigDecimal("5000.00"), new java.math.BigDecimal("1.0"), null, "USD"));
end

rule "LPI milestone payment rule"
when
    $f : PaymentTriggerFact(eventCode == "MILESTONE_REACHED_LPI")
then
    results.add(new PaymentRuleOutcome("INVESTIGATOR_FEES", new java.math.BigDecimal("3000.00"), new java.math.BigDecimal("1.0"), null, "USD"));
end
', true, now()
FROM rule_set WHERE name = 'PAYMENT_RULES_DEFAULT';

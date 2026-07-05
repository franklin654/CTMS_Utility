package com.ctms.ctms_backend.payment.service;

import com.ctms.ctms_backend.payment.rules.PaymentRuleOutcome;
import com.ctms.ctms_backend.payment.rules.PaymentTriggerFact;
import com.ctms.ctms_backend.rules.RuleSetService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Evaluates the shared PAYMENT_RULES_DEFAULT Drools rule set (Phase 0's RuleSetService/
 * DroolsRuleEngine, category PAYMENT, previously unused) for a given trigger event. Unlike
 * TaskRuleService, this returns Optional -- not every event code has a payment rule (e.g. LPO/DBL
 * milestones don't trigger a payment), and that's a normal, expected outcome, not an error. */
@Service
public class PaymentRuleService {

    private static final String PAYMENT_RULE_SET = "PAYMENT_RULES_DEFAULT";

    private final RuleSetService ruleSetService;

    public PaymentRuleService(RuleSetService ruleSetService) {
        this.ruleSetService = ruleSetService;
    }

    public Optional<PaymentRuleOutcome> evaluate(String eventCode) {
        List<Object> results = ruleSetService.evaluate(PAYMENT_RULE_SET, List.of(new PaymentTriggerFact(eventCode)));
        return results.stream().filter(PaymentRuleOutcome.class::isInstance).map(PaymentRuleOutcome.class::cast).findFirst();
    }
}

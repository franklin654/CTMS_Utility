package com.ctms.ctms_backend.task.service;

import com.ctms.ctms_backend.rules.RuleSetService;
import com.ctms.ctms_backend.task.rules.TaskRuleOutcome;
import com.ctms.ctms_backend.task.rules.TaskTriggerFact;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/** Evaluates the shared TASK_RULES_DEFAULT Drools rule set (Phase 0's RuleSetService/
 * DroolsRuleEngine, category WORKFLOW) for a given trigger event -- returns SLA hours, descriptive
 * owner/escalation role labels, and priority. Adding a new trigger event later means adding a DRL
 * rule to TASK_RULES_DEFAULT, not a new code branch here. */
@Service
public class TaskRuleService {

    private static final String TASK_RULE_SET = "TASK_RULES_DEFAULT";

    private final RuleSetService ruleSetService;

    public TaskRuleService(RuleSetService ruleSetService) {
        this.ruleSetService = ruleSetService;
    }

    public TaskRuleOutcome evaluate(String eventCode) {
        List<Object> results = ruleSetService.evaluate(TASK_RULE_SET, List.of(new TaskTriggerFact(eventCode)));
        return results.stream()
                .filter(TaskRuleOutcome.class::isInstance)
                .map(TaskRuleOutcome.class::cast)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No TASK_RULES_DEFAULT outcome for event: " + eventCode));
    }
}

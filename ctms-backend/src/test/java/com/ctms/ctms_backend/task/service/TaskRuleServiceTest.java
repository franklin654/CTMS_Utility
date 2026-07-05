package com.ctms.ctms_backend.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.rules.RuleSetService;
import com.ctms.ctms_backend.task.rules.TaskRuleOutcome;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskRuleServiceTest {

    @Mock private RuleSetService ruleSetService;

    @InjectMocks
    private TaskRuleService taskRuleService;

    @Test
    void evaluate_subjectEnrolled_returnsOutcome() {
        TaskRuleOutcome outcome = new TaskRuleOutcome(48, "SITE_COORDINATOR", "STUDY_MANAGER", "MEDIUM");
        when(ruleSetService.evaluate(eq("TASK_RULES_DEFAULT"), anyList())).thenReturn(List.of(outcome));

        TaskRuleOutcome result = taskRuleService.evaluate("SUBJECT_ENROLLED");

        assertEquals(48, result.getSlaHours());
        assertEquals("SITE_COORDINATOR", result.getOwnerRole());
        assertEquals("STUDY_MANAGER", result.getEscalationRole());
        assertEquals("MEDIUM", result.getPriority());
    }

    @Test
    void evaluate_siteActivated_returnsOutcome() {
        TaskRuleOutcome outcome = new TaskRuleOutcome(72, "STUDY_MANAGER", "ADMIN", "MEDIUM");
        when(ruleSetService.evaluate(eq("TASK_RULES_DEFAULT"), anyList())).thenReturn(List.of(outcome));

        TaskRuleOutcome result = taskRuleService.evaluate("SITE_ACTIVATED");

        assertEquals(72, result.getSlaHours());
        assertEquals("ADMIN", result.getEscalationRole());
    }

    @Test
    void evaluate_visitMissed_returnsOutcome() {
        TaskRuleOutcome outcome = new TaskRuleOutcome(24, "SITE_COORDINATOR", "STUDY_MANAGER", "HIGH");
        when(ruleSetService.evaluate(eq("TASK_RULES_DEFAULT"), anyList())).thenReturn(List.of(outcome));

        TaskRuleOutcome result = taskRuleService.evaluate("VISIT_MISSED");

        assertEquals(24, result.getSlaHours());
        assertEquals("HIGH", result.getPriority());
    }

    @Test
    void evaluate_noMatchingRule_throws() {
        when(ruleSetService.evaluate(eq("TASK_RULES_DEFAULT"), anyList())).thenReturn(List.of());
        assertThrows(NoSuchElementException.class, () -> taskRuleService.evaluate("UNKNOWN_EVENT"));
    }
}

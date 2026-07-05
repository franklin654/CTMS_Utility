package com.ctms.ctms_backend.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuleSetServiceTest {

    @Mock private RuleSetRepository ruleSetRepository;
    @Mock private RuleDefinitionRepository ruleDefinitionRepository;
    @Mock private UserRepository userRepository;
    @Mock private DroolsRuleEngine droolsRuleEngine;
    @Mock private AuditService auditService;

    @InjectMocks
    private RuleSetService ruleSetService;

    private RuleSet ruleSet;

    @BeforeEach
    void setUp() {
        ruleSet = new RuleSet();
        ruleSet.setId(1L);
        ruleSet.setName("TASK_RULES_DEFAULT");
        ruleSet.setCategory(RuleSet.CATEGORY_WORKFLOW);
        ruleSet.setDescription("Task SLA rules");
        ruleSet.setActive(true);
    }

    @Test
    void list_returnsSummaryWithLatestVersionNumber() {
        lenient().when(ruleSetRepository.findAll()).thenReturn(List.of(ruleSet));
        when(ruleDefinitionRepository.countByRuleSetId(1L)).thenReturn(2);

        List<RuleSetSummaryResponse> result = ruleSetService.list();

        assertEquals(1, result.size());
        assertEquals("TASK_RULES_DEFAULT", result.get(0).name());
        assertEquals(2, result.get(0).latestVersion());
    }

    @Test
    void getDetail_returnsFullVersionHistory() {
        when(ruleSetRepository.findByName("TASK_RULES_DEFAULT")).thenReturn(Optional.of(ruleSet));

        RuleDefinition v1 = new RuleDefinition();
        v1.setId(10L);
        v1.setRuleSet(ruleSet);
        v1.setVersion(1);
        v1.setDrlContent("package workflow; rule \"v1\" when then end");
        v1.setActive(false);

        RuleDefinition v2 = new RuleDefinition();
        v2.setId(11L);
        v2.setRuleSet(ruleSet);
        v2.setVersion(2);
        v2.setDrlContent("package workflow; rule \"v2\" when then end");
        v2.setActive(true);

        when(ruleDefinitionRepository.findByRuleSetIdOrderByVersionDesc(1L)).thenReturn(List.of(v2, v1));

        RuleSetDetailResponse detail = ruleSetService.getDetail("TASK_RULES_DEFAULT");

        assertEquals("TASK_RULES_DEFAULT", detail.name());
        assertEquals(2, detail.definitions().size());
        assertEquals(2, detail.definitions().get(0).version());
        assertEquals(true, detail.definitions().get(0).active());
    }

    @Test
    void getDetail_unknownRuleSet_throws() {
        when(ruleSetRepository.findByName("UNKNOWN")).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> ruleSetService.getDetail("UNKNOWN"));
    }
}

package com.ctms.ctms_backend.rules;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.internal.utils.KieHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleSetService {

    private final RuleSetRepository ruleSetRepository;
    private final RuleDefinitionRepository ruleDefinitionRepository;
    private final UserRepository userRepository;
    private final DroolsRuleEngine droolsRuleEngine;
    private final AuditService auditService;

    public RuleSetService(
            RuleSetRepository ruleSetRepository,
            RuleDefinitionRepository ruleDefinitionRepository,
            UserRepository userRepository,
            DroolsRuleEngine droolsRuleEngine,
            AuditService auditService) {
        this.ruleSetRepository = ruleSetRepository;
        this.ruleDefinitionRepository = ruleDefinitionRepository;
        this.userRepository = userRepository;
        this.droolsRuleEngine = droolsRuleEngine;
        this.auditService = auditService;
    }

    @Transactional
    public RuleSet createRuleSet(String name, String category, String description) {
        RuleSet ruleSet = new RuleSet();
        ruleSet.setName(name);
        ruleSet.setCategory(category);
        ruleSet.setDescription(description);
        ruleSet.setActive(true);
        ruleSet = ruleSetRepository.save(ruleSet);
        auditService.record("RuleSet", String.valueOf(ruleSet.getId()), AuditAction.CREATE, null, name, null);
        return ruleSet;
    }

    /** Compiles the DRL before persisting -- a malformed rule never becomes "active" and breaks a
     * live workflow; this is the Phase 0-level validation gate, ahead of Phase 10's full no-code UI. */
    @Transactional
    public RuleDefinition addDefinition(String ruleSetName, String drlContent, String createdByUsername) {
        RuleSet ruleSet = ruleSetRepository.findByName(ruleSetName).orElseThrow(NoSuchElementException::new);
        validateCompiles(drlContent);

        ruleDefinitionRepository.findByRuleSetIdAndActiveTrue(ruleSet.getId()).ifPresent(previous -> {
            previous.setActive(false);
            ruleDefinitionRepository.save(previous);
        });

        RuleDefinition definition = new RuleDefinition();
        definition.setRuleSet(ruleSet);
        definition.setVersion(ruleDefinitionRepository.countByRuleSetId(ruleSet.getId()) + 1);
        definition.setDrlContent(drlContent);
        definition.setActive(true);
        if (createdByUsername != null) {
            User user = userRepository.findByUsername(createdByUsername).orElseThrow(InvalidCredentialsException::new);
            definition.setCreatedBy(user);
        }
        definition = ruleDefinitionRepository.save(definition);

        auditService.record(
                "RuleSet", String.valueOf(ruleSet.getId()), AuditAction.UPDATE, null,
                "activated version " + definition.getVersion(), null);
        return definition;
    }

    @Transactional(readOnly = true)
    public List<Object> evaluate(String ruleSetName, List<Object> facts) {
        RuleSet ruleSet = ruleSetRepository.findByName(ruleSetName).orElseThrow(NoSuchElementException::new);
        RuleDefinition active = ruleDefinitionRepository
                .findByRuleSetIdAndActiveTrue(ruleSet.getId())
                .orElseThrow(() -> new NoSuchElementException("No active rule definition for " + ruleSetName));
        return droolsRuleEngine.fireRules(active.getDrlContent(), facts);
    }

    private void validateCompiles(String drlContent) {
        try {
            KieBase kieBase = new KieHelper().addContent(drlContent, ResourceType.DRL).build();
            // KieHelper.build() throws on compile errors; reaching here means the DRL is valid.
            kieBase.newKieSession().dispose();
        } catch (RuntimeException e) {
            throw new RuleCompilationException("DRL failed to compile: " + e.getMessage(), e);
        }
    }
}

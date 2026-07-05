package com.ctms.ctms_backend.rules;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleDefinitionRepository extends JpaRepository<RuleDefinition, Long> {

    List<RuleDefinition> findByRuleSetIdOrderByVersionDesc(Long ruleSetId);

    Optional<RuleDefinition> findByRuleSetIdAndActiveTrue(Long ruleSetId);

    int countByRuleSetId(Long ruleSetId);
}

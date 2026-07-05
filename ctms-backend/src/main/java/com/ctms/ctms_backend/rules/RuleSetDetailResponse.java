package com.ctms.ctms_backend.rules;

import java.util.List;

public record RuleSetDetailResponse(
        Long id, String name, String category, String description, boolean active, List<RuleDefinitionDetailResponse> definitions) {

    static RuleSetDetailResponse from(RuleSet ruleSet, List<RuleDefinition> definitions) {
        return new RuleSetDetailResponse(
                ruleSet.getId(),
                ruleSet.getName(),
                ruleSet.getCategory(),
                ruleSet.getDescription(),
                ruleSet.isActive(),
                definitions.stream().map(RuleDefinitionDetailResponse::from).toList());
    }
}

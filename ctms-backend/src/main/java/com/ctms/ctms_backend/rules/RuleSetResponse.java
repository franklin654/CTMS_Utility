package com.ctms.ctms_backend.rules;

public record RuleSetResponse(Long id, String name, String category, String description, boolean active) {

    static RuleSetResponse from(RuleSet r) {
        return new RuleSetResponse(r.getId(), r.getName(), r.getCategory(), r.getDescription(), r.isActive());
    }
}

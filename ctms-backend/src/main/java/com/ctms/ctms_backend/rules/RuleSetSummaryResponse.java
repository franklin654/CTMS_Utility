package com.ctms.ctms_backend.rules;

public record RuleSetSummaryResponse(Long id, String name, String category, boolean active, int latestVersion) {

    static RuleSetSummaryResponse from(RuleSet r, int latestVersion) {
        return new RuleSetSummaryResponse(r.getId(), r.getName(), r.getCategory(), r.isActive(), latestVersion);
    }
}

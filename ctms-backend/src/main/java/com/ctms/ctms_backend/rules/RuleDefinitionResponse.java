package com.ctms.ctms_backend.rules;

import java.time.Instant;

public record RuleDefinitionResponse(Long id, int version, boolean active, Instant createdAt) {

    static RuleDefinitionResponse from(RuleDefinition d) {
        return new RuleDefinitionResponse(d.getId(), d.getVersion(), d.isActive(), d.getCreatedAt());
    }
}

package com.ctms.ctms_backend.rules;

import java.time.Instant;

/** Unlike RuleDefinitionResponse, includes drlContent -- needed to pre-fill the rule editor's
 * textarea with the active version's text. */
public record RuleDefinitionDetailResponse(Long id, int version, boolean active, String drlContent, Instant createdAt) {

    static RuleDefinitionDetailResponse from(RuleDefinition d) {
        return new RuleDefinitionDetailResponse(d.getId(), d.getVersion(), d.isActive(), d.getDrlContent(), d.getCreatedAt());
    }
}

package com.ctms.ctms_backend.audit;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        String entityName,
        String entityId,
        String action,
        String performedByUsername,
        Instant performedAt,
        String beforeValue,
        String afterValue,
        String reason) {

    static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getEntityName(),
                log.getEntityId(),
                log.getAction(),
                log.getPerformedBy() == null ? null : log.getPerformedBy().getUsername(),
                log.getPerformedAt(),
                log.getBeforeValue(),
                log.getAfterValue(),
                log.getReason());
    }
}

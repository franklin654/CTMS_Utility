package com.ctms.ctms_backend.task.dto;

import com.ctms.ctms_backend.task.entity.Task;
import java.time.Instant;

public record TaskResponse(
        Long id,
        String eventCode,
        String title,
        String description,
        String entityName,
        Long entityId,
        String ownerUsername,
        String ownerRole,
        String escalationTargetUsername,
        String escalationRole,
        String priority,
        String status,
        Instant dueAt,
        boolean escalated,
        Instant escalatedAt,
        Instant completedAt,
        Instant createdAt) {

    public static TaskResponse from(Task t) {
        return new TaskResponse(
                t.getId(),
                t.getEventCode(),
                t.getTitle(),
                t.getDescription(),
                t.getEntityName(),
                t.getEntityId(),
                t.getOwner().getUsername(),
                t.getOwnerRole(),
                t.getEscalationTarget().getUsername(),
                t.getEscalationRole(),
                t.getPriority().name(),
                t.getStatus().name(),
                t.getDueAt(),
                t.isEscalated(),
                t.getEscalatedAt(),
                t.getCompletedAt(),
                t.getCreatedAt());
    }
}

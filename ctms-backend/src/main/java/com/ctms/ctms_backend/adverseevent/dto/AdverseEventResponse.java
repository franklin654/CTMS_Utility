package com.ctms.ctms_backend.adverseevent.dto;

import com.ctms.ctms_backend.adverseevent.entity.AdverseEvent;
import java.time.Instant;

public record AdverseEventResponse(
        Long id,
        Long subjectId,
        String subjectCode,
        Long visitId,
        String description,
        String severity,
        String status,
        String resolutionNotes,
        Instant resolvedAt,
        String createdByUsername,
        Instant createdAt) {

    public static AdverseEventResponse from(AdverseEvent ae) {
        return new AdverseEventResponse(
                ae.getId(),
                ae.getSubject().getId(),
                ae.getSubject().getSubjectCode(),
                ae.getVisit() != null ? ae.getVisit().getId() : null,
                ae.getDescription(),
                ae.getSeverity().name(),
                ae.getStatus().name(),
                ae.getResolutionNotes(),
                ae.getResolvedAt(),
                ae.getCreatedBy().getUsername(),
                ae.getCreatedAt());
    }
}

package com.ctms.ctms_backend.monitoring.dto;

import com.ctms.ctms_backend.monitoring.entity.MonitoringVisit;
import java.time.Instant;
import java.time.LocalDate;

public record MonitoringVisitResponse(
        Long id,
        Long siteId,
        String siteCode,
        String craUsername,
        String visitType,
        LocalDate visitDate,
        String findings,
        String issuesIdentified,
        String checklistNotes,
        String createdByUsername,
        Instant createdAt,
        String modifiedByUsername,
        Instant modifiedAt) {

    public static MonitoringVisitResponse from(MonitoringVisit v) {
        return new MonitoringVisitResponse(
                v.getId(),
                v.getSite().getId(),
                v.getSite().getSiteCode(),
                v.getCra().getUsername(),
                v.getVisitType().name(),
                v.getVisitDate(),
                v.getFindings(),
                v.getIssuesIdentified(),
                v.getChecklistNotes(),
                v.getCreatedBy().getUsername(),
                v.getCreatedAt(),
                v.getModifiedBy().getUsername(),
                v.getModifiedAt());
    }
}

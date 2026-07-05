package com.ctms.ctms_backend.study.dto;

import com.ctms.ctms_backend.study.entity.Study;
import java.time.Instant;
import java.time.LocalDate;

public record StudyResponse(
        Long id,
        String studyCode,
        String name,
        String protocolId,
        String protocolVersion,
        String phase,
        String sponsor,
        String status,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        LocalDate actualStartDate,
        LocalDate actualEndDate,
        String description,
        String createdByUsername,
        String modifiedByUsername,
        Instant createdAt,
        Instant modifiedAt) {

    public static StudyResponse from(Study s) {
        return new StudyResponse(
                s.getId(),
                s.getStudyCode(),
                s.getName(),
                s.getProtocolId(),
                s.getProtocolVersion(),
                s.getPhase(),
                s.getSponsor(),
                s.getStatus().name(),
                s.getPlannedStartDate(),
                s.getPlannedEndDate(),
                s.getActualStartDate(),
                s.getActualEndDate(),
                s.getDescription(),
                s.getCreatedBy().getUsername(),
                s.getModifiedBy().getUsername(),
                s.getCreatedAt(),
                s.getModifiedAt());
    }
}

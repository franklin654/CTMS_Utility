package com.ctms.ctms_backend.milestone.dto;

import com.ctms.ctms_backend.milestone.entity.Milestone;
import java.time.Instant;
import java.time.LocalDate;

public record MilestoneResponse(
        Long id,
        Long studyId,
        String studyCode,
        String milestoneType,
        LocalDate plannedDate,
        LocalDate actualDate,
        boolean delayed,
        String createdByUsername,
        Instant createdAt) {

    public static MilestoneResponse from(Milestone m) {
        LocalDate today = LocalDate.now();
        boolean delayed = m.getActualDate() == null
                ? today.isAfter(m.getPlannedDate())
                : m.getActualDate().isAfter(m.getPlannedDate());
        return new MilestoneResponse(
                m.getId(),
                m.getStudy().getId(),
                m.getStudy().getStudyCode(),
                m.getMilestoneType().name(),
                m.getPlannedDate(),
                m.getActualDate(),
                delayed,
                m.getCreatedBy().getUsername(),
                m.getCreatedAt());
    }
}

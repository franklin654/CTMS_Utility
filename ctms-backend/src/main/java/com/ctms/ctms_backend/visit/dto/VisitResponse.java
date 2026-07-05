package com.ctms.ctms_backend.visit.dto;

import com.ctms.ctms_backend.visit.entity.Visit;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record VisitResponse(
        Long id,
        Long subjectId,
        Long visitTemplateId,
        boolean adHoc,
        String name,
        Integer sequenceNumber,
        Integer targetDay,
        Integer windowEarlyDays,
        Integer windowLateDays,
        String requiredProcedures,
        String visitType,
        LocalDate scheduledDate,
        String status,
        LocalDate actualDate,
        LocalTime actualTime,
        String notes,
        String reasonCode,
        Long rescheduledFromVisitId,
        Instant completedAt) {

    public static VisitResponse from(Visit v) {
        return new VisitResponse(
                v.getId(),
                v.getSubject().getId(),
                v.getVisitTemplate() != null ? v.getVisitTemplate().getId() : null,
                v.getVisitTemplate() == null,
                v.getName(),
                v.getSequenceNumber(),
                v.getTargetDay(),
                v.getWindowEarlyDays(),
                v.getWindowLateDays(),
                v.getRequiredProcedures(),
                v.getVisitType().name(),
                v.getScheduledDate(),
                v.getStatus().name(),
                v.getActualDate(),
                v.getActualTime(),
                v.getNotes(),
                v.getReasonCode(),
                v.getRescheduledFromVisit() != null ? v.getRescheduledFromVisit().getId() : null,
                v.getCompletedAt());
    }
}

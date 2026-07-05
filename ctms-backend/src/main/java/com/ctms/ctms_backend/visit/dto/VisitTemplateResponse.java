package com.ctms.ctms_backend.visit.dto;

import com.ctms.ctms_backend.visit.entity.VisitTemplate;

public record VisitTemplateResponse(
        Long id,
        Long studyId,
        String name,
        Integer sequenceNumber,
        Integer targetDay,
        Integer windowEarlyDays,
        Integer windowLateDays,
        String requiredProcedures,
        String visitType,
        boolean active) {

    public static VisitTemplateResponse from(VisitTemplate t) {
        return new VisitTemplateResponse(
                t.getId(),
                t.getStudy().getId(),
                t.getName(),
                t.getSequenceNumber(),
                t.getTargetDay(),
                t.getWindowEarlyDays(),
                t.getWindowLateDays(),
                t.getRequiredProcedures(),
                t.getVisitType().name(),
                t.isActive());
    }
}

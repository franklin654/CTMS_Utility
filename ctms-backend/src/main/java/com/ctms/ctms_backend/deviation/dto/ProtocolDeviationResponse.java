package com.ctms.ctms_backend.deviation.dto;

import com.ctms.ctms_backend.deviation.entity.ProtocolDeviation;
import java.time.Instant;
import java.time.LocalDate;

public record ProtocolDeviationResponse(
        Long id,
        Long subjectId,
        String subjectCode,
        String description,
        String severity,
        LocalDate deviationDate,
        String createdByUsername,
        Instant createdAt) {

    public static ProtocolDeviationResponse from(ProtocolDeviation d) {
        return new ProtocolDeviationResponse(
                d.getId(),
                d.getSubject().getId(),
                d.getSubject().getSubjectCode(),
                d.getDescription(),
                d.getSeverity().name(),
                d.getDeviationDate(),
                d.getCreatedBy().getUsername(),
                d.getCreatedAt());
    }
}

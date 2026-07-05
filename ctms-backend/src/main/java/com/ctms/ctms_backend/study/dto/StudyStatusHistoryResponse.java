package com.ctms.ctms_backend.study.dto;

import com.ctms.ctms_backend.study.entity.StudyStatusHistory;
import java.time.Instant;

public record StudyStatusHistoryResponse(
        Long id,
        String fromStatus,
        String toStatus,
        String justification,
        String changedByUsername,
        Instant changedAt,
        boolean signed) {

    public static StudyStatusHistoryResponse from(StudyStatusHistory h) {
        return new StudyStatusHistoryResponse(
                h.getId(),
                h.getFromStatus() == null ? null : h.getFromStatus().name(),
                h.getToStatus().name(),
                h.getJustification(),
                h.getChangedBy().getUsername(),
                h.getChangedAt(),
                h.getEsignature() != null);
    }
}

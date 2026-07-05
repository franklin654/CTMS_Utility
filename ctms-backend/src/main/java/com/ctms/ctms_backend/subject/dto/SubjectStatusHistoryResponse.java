package com.ctms.ctms_backend.subject.dto;

import com.ctms.ctms_backend.subject.entity.SubjectStatusHistory;
import java.time.Instant;

public record SubjectStatusHistoryResponse(
        Long id, String fromStatus, String toStatus, String reasonCode, String changedByUsername, Instant changedAt) {

    public static SubjectStatusHistoryResponse from(SubjectStatusHistory h) {
        return new SubjectStatusHistoryResponse(
                h.getId(),
                h.getFromStatus() == null ? null : h.getFromStatus().name(),
                h.getToStatus().name(),
                h.getReasonCode(),
                h.getChangedBy().getUsername(),
                h.getChangedAt());
    }
}

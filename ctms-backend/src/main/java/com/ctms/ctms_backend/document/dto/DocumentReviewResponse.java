package com.ctms.ctms_backend.document.dto;

import com.ctms.ctms_backend.document.entity.DocumentReview;
import java.time.Instant;

public record DocumentReviewResponse(
        Long id,
        String stage,
        String action,
        String comment,
        String actedByUsername,
        Instant actedAt,
        boolean signed) {

    public static DocumentReviewResponse from(DocumentReview r) {
        return new DocumentReviewResponse(
                r.getId(),
                r.getStage().name(),
                r.getAction().name(),
                r.getComment(),
                r.getActedBy().getUsername(),
                r.getActedAt(),
                r.getEsignature() != null);
    }
}

package com.ctms.ctms_backend.testresult.dto;

import com.ctms.ctms_backend.testresult.entity.TestResult;
import java.time.Instant;

public record TestResultResponse(
        Long id,
        Long subjectId,
        String subjectCode,
        Long visitId,
        String visitName,
        String testName,
        String resultValue,
        String units,
        String referenceRange,
        boolean abnormal,
        String status,
        String notes,
        String reviewedByUsername,
        Instant reviewedAt,
        String createdByUsername,
        Instant createdAt) {

    public static TestResultResponse from(TestResult r) {
        return new TestResultResponse(
                r.getId(),
                r.getSubject().getId(),
                r.getSubject().getSubjectCode(),
                r.getVisit().getId(),
                r.getVisit().getName(),
                r.getTestName(),
                r.getResultValue(),
                r.getUnits(),
                r.getReferenceRange(),
                r.isAbnormal(),
                r.getStatus().name(),
                r.getNotes(),
                r.getReviewedBy() != null ? r.getReviewedBy().getUsername() : null,
                r.getReviewedAt(),
                r.getCreatedBy().getUsername(),
                r.getCreatedAt());
    }
}

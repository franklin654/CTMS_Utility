package com.ctms.ctms_backend.testresult.dto;

import com.ctms.ctms_backend.testresult.entity.TestResultAttachment;
import java.time.Instant;

public record TestResultAttachmentResponse(
        Long id,
        Long testResultId,
        String fileName,
        String contentType,
        long sizeBytes,
        String uploadedByUsername,
        Instant uploadedAt) {

    public static TestResultAttachmentResponse from(TestResultAttachment a) {
        return new TestResultAttachmentResponse(
                a.getId(),
                a.getTestResult().getId(),
                a.getFileName(),
                a.getContentType(),
                a.getSizeBytes(),
                a.getUploadedBy().getUsername(),
                a.getUploadedAt());
    }
}

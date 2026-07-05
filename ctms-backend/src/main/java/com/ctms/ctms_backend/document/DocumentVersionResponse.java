package com.ctms.ctms_backend.document;

import java.time.Instant;
import java.time.LocalDate;

public record DocumentVersionResponse(
        Long id,
        Long documentId,
        String documentTitle,
        int versionNumber,
        String fileName,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        LocalDate effectiveDate,
        String status,
        String uploadedByUsername,
        Instant uploadedAt) {

    public static DocumentVersionResponse from(DocumentVersion v) {
        return new DocumentVersionResponse(
                v.getId(),
                v.getDocument().getId(),
                v.getDocument().getTitle(),
                v.getVersionNumber(),
                v.getFileName(),
                v.getContentType(),
                v.getSizeBytes(),
                v.getChecksumSha256(),
                v.getEffectiveDate(),
                v.getStatus().name(),
                v.getUploadedBy().getUsername(),
                v.getUploadedAt());
    }
}

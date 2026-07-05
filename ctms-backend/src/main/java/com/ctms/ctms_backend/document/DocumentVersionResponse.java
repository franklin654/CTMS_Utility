package com.ctms.ctms_backend.document;

import java.time.Instant;

public record DocumentVersionResponse(
        Long id,
        int versionNumber,
        String fileName,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        String status,
        String uploadedByUsername,
        Instant uploadedAt) {

    static DocumentVersionResponse from(DocumentVersion v) {
        return new DocumentVersionResponse(
                v.getId(),
                v.getVersionNumber(),
                v.getFileName(),
                v.getContentType(),
                v.getSizeBytes(),
                v.getChecksumSha256(),
                v.getStatus(),
                v.getUploadedBy().getUsername(),
                v.getUploadedAt());
    }
}

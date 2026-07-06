package com.ctms.ctms_backend.document;

import java.time.Instant;

public record DocumentResponse(
        Long id,
        String title,
        String category,
        String ownerUsername,
        Long studyId,
        String studyCode,
        Long subjectId,
        String subjectCode,
        DocumentVersionResponse currentVersion,
        Instant createdAt,
        Instant updatedAt) {

    static DocumentResponse from(Document d) {
        return new DocumentResponse(
                d.getId(),
                d.getTitle(),
                d.getCategory(),
                d.getOwner() == null ? null : d.getOwner().getUsername(),
                d.getStudy() == null ? null : d.getStudy().getId(),
                d.getStudy() == null ? null : d.getStudy().getStudyCode(),
                d.getSubject() == null ? null : d.getSubject().getId(),
                d.getSubject() == null ? null : d.getSubject().getSubjectCode(),
                d.getCurrentVersion() == null ? null : DocumentVersionResponse.from(d.getCurrentVersion()),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }
}

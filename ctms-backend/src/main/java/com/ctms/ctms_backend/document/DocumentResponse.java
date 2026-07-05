package com.ctms.ctms_backend.document;

import java.time.Instant;

public record DocumentResponse(
        Long id,
        String title,
        String category,
        String ownerUsername,
        DocumentVersionResponse currentVersion,
        Instant createdAt,
        Instant updatedAt) {

    static DocumentResponse from(Document d) {
        return new DocumentResponse(
                d.getId(),
                d.getTitle(),
                d.getCategory(),
                d.getOwner() == null ? null : d.getOwner().getUsername(),
                d.getCurrentVersion() == null ? null : DocumentVersionResponse.from(d.getCurrentVersion()),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }
}

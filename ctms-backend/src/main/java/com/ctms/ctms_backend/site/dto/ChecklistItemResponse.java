package com.ctms.ctms_backend.site.dto;

import com.ctms.ctms_backend.site.entity.SiteActivationChecklistItem;
import java.time.Instant;
import java.time.LocalDate;

public record ChecklistItemResponse(
        Long id,
        String itemType,
        String status,
        LocalDate completedDate,
        String note,
        String updatedByUsername,
        Instant updatedAt) {

    public static ChecklistItemResponse from(SiteActivationChecklistItem item) {
        return new ChecklistItemResponse(
                item.getId(),
                item.getItemType().name(),
                item.getStatus().name(),
                item.getCompletedDate(),
                item.getNote(),
                item.getUpdatedBy() == null ? null : item.getUpdatedBy().getUsername(),
                item.getUpdatedAt());
    }
}

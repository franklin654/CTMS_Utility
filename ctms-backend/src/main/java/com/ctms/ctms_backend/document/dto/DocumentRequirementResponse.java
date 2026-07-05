package com.ctms.ctms_backend.document.dto;

import com.ctms.ctms_backend.document.entity.DocumentRequirement;

public record DocumentRequirementResponse(
        Long id, Long studyId, String studyCode, String studyPhase, String documentCategory, boolean mandatory, String createdByUsername) {

    public static DocumentRequirementResponse from(DocumentRequirement r) {
        return new DocumentRequirementResponse(
                r.getId(),
                r.getStudy().getId(),
                r.getStudy().getStudyCode(),
                r.getStudyPhase().name(),
                r.getDocumentCategory(),
                r.isMandatory(),
                r.getCreatedBy().getUsername());
    }
}

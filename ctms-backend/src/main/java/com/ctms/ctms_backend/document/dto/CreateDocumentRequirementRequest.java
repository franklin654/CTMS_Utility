package com.ctms.ctms_backend.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDocumentRequirementRequest(
        @NotNull Long studyId, @NotBlank String studyPhase, @NotBlank String documentCategory, boolean mandatory) {}

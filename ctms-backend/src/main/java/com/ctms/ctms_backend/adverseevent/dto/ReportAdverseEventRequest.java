package com.ctms.ctms_backend.adverseevent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReportAdverseEventRequest(
        @NotNull Long subjectId,
        Long visitId,
        @NotBlank String description,
        @NotBlank String severity) {}

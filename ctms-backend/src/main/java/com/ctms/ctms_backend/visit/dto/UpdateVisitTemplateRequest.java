package com.ctms.ctms_backend.visit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateVisitTemplateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull Integer sequenceNumber,
        @NotNull Integer targetDay,
        @NotNull Integer windowEarlyDays,
        @NotNull Integer windowLateDays,
        String requiredProcedures,
        @NotBlank String visitType) {}

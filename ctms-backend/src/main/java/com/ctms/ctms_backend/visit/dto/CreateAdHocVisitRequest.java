package com.ctms.ctms_backend.visit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateAdHocVisitRequest(
        @NotBlank String name,
        @NotNull LocalDate scheduledDate,
        @NotBlank String visitType,
        String requiredProcedures,
        @NotBlank String reasonCode) {}

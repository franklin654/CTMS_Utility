package com.ctms.ctms_backend.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record LogMonitoringVisitRequest(
        @NotNull Long siteId,
        @NotBlank String visitType,
        @NotNull LocalDate visitDate,
        String findings,
        String issuesIdentified,
        String checklistNotes) {}

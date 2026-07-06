package com.ctms.ctms_backend.deviation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ReportProtocolDeviationRequest(
        @NotNull Long subjectId,
        @NotBlank String description,
        @NotBlank String severity,
        @NotNull LocalDate deviationDate) {}

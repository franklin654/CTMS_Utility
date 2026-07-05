package com.ctms.ctms_backend.testresult.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTestResultRequest(
        @NotNull Long subjectId,
        @NotNull Long visitId,
        @NotBlank String testName,
        @NotBlank String resultValue,
        String units,
        String referenceRange,
        boolean abnormal,
        String notes) {}

package com.ctms.ctms_backend.subject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEligibilityCriterionRequest(
        @NotNull Long studyId, @NotBlank @Size(max = 500) String label, @NotBlank String criterionType) {}

package com.ctms.ctms_backend.esignature;

import jakarta.validation.constraints.NotBlank;

public record SignRequest(
        @NotBlank String password,
        @NotBlank String entityName,
        @NotBlank String entityId,
        @NotBlank String reason) {}

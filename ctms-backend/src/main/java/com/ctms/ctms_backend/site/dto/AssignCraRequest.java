package com.ctms.ctms_backend.site.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignCraRequest(@NotBlank String craUsername, String backupCraUsername) {}

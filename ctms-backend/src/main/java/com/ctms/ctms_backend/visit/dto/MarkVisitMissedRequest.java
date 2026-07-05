package com.ctms.ctms_backend.visit.dto;

import jakarta.validation.constraints.NotBlank;

public record MarkVisitMissedRequest(@NotBlank String reasonCode) {}

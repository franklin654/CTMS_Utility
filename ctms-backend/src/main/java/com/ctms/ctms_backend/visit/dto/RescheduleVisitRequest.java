package com.ctms.ctms_backend.visit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RescheduleVisitRequest(@NotNull LocalDate newDate, @NotBlank String reasonCode) {}

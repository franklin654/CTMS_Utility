package com.ctms.ctms_backend.visit.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record MarkVisitCompletedRequest(@NotNull LocalDate actualDate, LocalTime actualTime, String notes) {}

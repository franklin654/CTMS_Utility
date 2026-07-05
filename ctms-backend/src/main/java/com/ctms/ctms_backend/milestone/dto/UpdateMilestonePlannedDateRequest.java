package com.ctms.ctms_backend.milestone.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record UpdateMilestonePlannedDateRequest(@NotNull LocalDate plannedDate) {}

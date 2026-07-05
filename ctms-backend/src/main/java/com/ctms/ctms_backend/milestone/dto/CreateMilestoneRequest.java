package com.ctms.ctms_backend.milestone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateMilestoneRequest(@NotNull Long studyId, @NotBlank String milestoneType, @NotNull LocalDate plannedDate) {}

package com.ctms.ctms_backend.adverseevent.dto;

import jakarta.validation.constraints.NotBlank;

public record TransitionAdverseEventRequest(@NotBlank String targetStatus, @NotBlank String justification) {}

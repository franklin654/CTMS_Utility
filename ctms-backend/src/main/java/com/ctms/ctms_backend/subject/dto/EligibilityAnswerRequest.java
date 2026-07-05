package com.ctms.ctms_backend.subject.dto;

import jakarta.validation.constraints.NotNull;

public record EligibilityAnswerRequest(@NotNull Long criterionId, boolean met) {}

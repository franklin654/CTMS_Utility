package com.ctms.ctms_backend.rules;

import jakarta.validation.constraints.NotBlank;

public record CreateRuleSetRequest(@NotBlank String name, @NotBlank String category, String description) {}

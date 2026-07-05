package com.ctms.ctms_backend.rules;

import jakarta.validation.constraints.NotBlank;

public record AddRuleDefinitionRequest(@NotBlank String drlContent) {}

package com.ctms.ctms_backend.budget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record BudgetLineItemRequest(@NotBlank String costCategory, @NotNull BigDecimal plannedAmount, @NotBlank String currency) {}

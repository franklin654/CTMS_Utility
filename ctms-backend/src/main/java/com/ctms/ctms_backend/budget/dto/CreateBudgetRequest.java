package com.ctms.ctms_backend.budget.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateBudgetRequest(@NotNull Long studyId, @NotEmpty List<BudgetLineItemRequest> lineItems) {}

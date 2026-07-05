package com.ctms.ctms_backend.budget.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** `reason` is intentionally not `@NotBlank` here -- required for version 2+, enforced in
 * BudgetService, not the DTO, since the very first version needs no reason (nothing is being
 * changed yet) and reuses this same shape via CreateBudgetRequest instead. */
public record CreateBudgetVersionRequest(@NotEmpty List<BudgetLineItemRequest> lineItems, String reason) {}

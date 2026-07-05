package com.ctms.ctms_backend.budget.dto;

import java.math.BigDecimal;

public record BudgetLineItemResponse(
        String costCategory, BigDecimal plannedAmount, BigDecimal actualAmount, BigDecimal variance, String currency) {}

package com.ctms.ctms_backend.budget.dto;

import java.time.Instant;
import java.util.List;

public record BudgetVersionResponse(
        Long id,
        Long studyId,
        String studyCode,
        int versionNumber,
        String status,
        String reason,
        List<BudgetLineItemResponse> lineItems,
        String createdByUsername,
        Instant createdAt) {}

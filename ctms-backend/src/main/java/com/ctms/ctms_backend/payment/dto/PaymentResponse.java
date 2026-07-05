package com.ctms.ctms_backend.payment.dto;

import com.ctms.ctms_backend.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long studyId,
        String studyCode,
        Long siteId,
        String siteCode,
        String costCategory,
        String eventCode,
        String triggerEntityName,
        Long triggerEntityId,
        BigDecimal baseAmount,
        BigDecimal multiplier,
        BigDecimal capAmount,
        BigDecimal amount,
        String currency,
        String status,
        String holdReason,
        Instant heldAt,
        String heldByUsername,
        String releaseReason,
        Instant releasedAt,
        String releasedByUsername,
        String createdByUsername,
        Instant createdAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getStudy().getId(),
                p.getStudy().getStudyCode(),
                p.getSite() != null ? p.getSite().getId() : null,
                p.getSite() != null ? p.getSite().getSiteCode() : null,
                p.getCostCategory().name(),
                p.getEventCode(),
                p.getTriggerEntityName(),
                p.getTriggerEntityId(),
                p.getBaseAmount(),
                p.getMultiplier(),
                p.getCapAmount(),
                p.getAmount(),
                p.getCurrency(),
                p.getStatus().name(),
                p.getHoldReason(),
                p.getHeldAt(),
                p.getHeldBy() != null ? p.getHeldBy().getUsername() : null,
                p.getReleaseReason(),
                p.getReleasedAt(),
                p.getReleasedBy() != null ? p.getReleasedBy().getUsername() : null,
                p.getCreatedBy().getUsername(),
                p.getCreatedAt());
    }
}

package com.ctms.ctms_backend.dashboard.dto;

public record HighRiskSiteResponse(
        Long siteId, String siteCode, String name, double missedVisitRatePercent, long openHighSeverityAeCount) {}

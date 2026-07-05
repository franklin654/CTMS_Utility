package com.ctms.ctms_backend.dashboard.dto;

import com.ctms.ctms_backend.milestone.dto.MilestoneResponse;
import java.util.List;
import java.util.Map;

public record DashboardSummaryResponse(
        Map<String, Long> enrollmentByStatus,
        Map<String, Long> siteActivationByStatus,
        Double visitAdherenceRatePercent,
        Map<String, Long> sitesByCountry,
        List<HighRiskSiteResponse> highRiskSites,
        List<MilestoneResponse> milestones) {}

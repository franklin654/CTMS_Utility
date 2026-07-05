package com.ctms.ctms_backend.visit.dto;

import java.util.List;

public record SubjectVisitScheduleResponse(List<VisitResponse> visits, double complianceRate) {}

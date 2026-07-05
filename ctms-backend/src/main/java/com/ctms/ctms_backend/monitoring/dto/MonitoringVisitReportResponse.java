package com.ctms.ctms_backend.monitoring.dto;

import com.ctms.ctms_backend.monitoring.entity.MonitoringVisitReport;
import java.time.Instant;

public record MonitoringVisitReportResponse(
        Long id,
        Long monitoringVisitId,
        String fileName,
        String contentType,
        long sizeBytes,
        String uploadedByUsername,
        Instant uploadedAt) {

    public static MonitoringVisitReportResponse from(MonitoringVisitReport r) {
        return new MonitoringVisitReportResponse(
                r.getId(),
                r.getMonitoringVisit().getId(),
                r.getFileName(),
                r.getContentType(),
                r.getSizeBytes(),
                r.getUploadedBy().getUsername(),
                r.getUploadedAt());
    }
}

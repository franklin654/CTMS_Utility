package com.ctms.ctms_backend.monitoring.controller;

import com.ctms.ctms_backend.monitoring.dto.MonitoringVisitReportResponse;
import com.ctms.ctms_backend.monitoring.entity.MonitoringVisit;
import com.ctms.ctms_backend.monitoring.entity.MonitoringVisitReport;
import com.ctms.ctms_backend.monitoring.service.MonitoringVisitReportService;
import com.ctms.ctms_backend.monitoring.service.MonitoringVisitService;
import java.io.InputStream;
import java.security.Principal;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MonitoringVisitReportController {

    private static final String WRITE_ROLES = "hasAnyRole('CRA_MONITOR','ADMIN')";
    private static final String READ_ROLES =
            "hasAnyRole('STUDY_MANAGER','ADMIN','SITE_COORDINATOR','INVESTIGATOR','CRA_MONITOR',"
                    + "'DATA_MANAGEMENT','FINANCE_MANAGER','QA_COMPLIANCE_AUDITOR','CLINICAL_LEADERSHIP',"
                    + "'EXECUTIVE','SPONSOR_CRO_LEADERSHIP')";

    private final MonitoringVisitReportService reportService;
    private final MonitoringVisitService monitoringVisitService;

    public MonitoringVisitReportController(MonitoringVisitReportService reportService, MonitoringVisitService monitoringVisitService) {
        this.reportService = reportService;
        this.monitoringVisitService = monitoringVisitService;
    }

    @PostMapping(value = "/api/monitoring-visits/{id}/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE_ROLES)
    public MonitoringVisitReportResponse upload(
            Principal principal, @PathVariable Long id, @RequestPart MultipartFile file) {
        MonitoringVisit visit = monitoringVisitService.findMonitoringVisit(id);
        return reportService.upload(visit, file, principal.getName());
    }

    @GetMapping("/api/monitoring-visits/{id}/reports")
    @PreAuthorize(READ_ROLES)
    public List<MonitoringVisitReportResponse> list(@PathVariable Long id) {
        return reportService.list(id);
    }

    @GetMapping("/api/monitoring-visit-reports/{id}/download")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        MonitoringVisitReport report = reportService.findReport(id);
        InputStream content = reportService.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + report.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(report.getContentType()))
                .body(new InputStreamResource(content));
    }
}

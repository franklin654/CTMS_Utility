package com.ctms.ctms_backend.dashboard.controller;

import com.ctms.ctms_backend.dashboard.dto.DashboardFilterOptionsResponse;
import com.ctms.ctms_backend.dashboard.dto.DashboardSummaryResponse;
import com.ctms.ctms_backend.dashboard.service.DashboardExportService;
import com.ctms.ctms_backend.dashboard.service.DashboardService;
import java.security.Principal;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final String READ_ROLES =
            "hasAnyRole('STUDY_MANAGER','ADMIN','SITE_COORDINATOR','INVESTIGATOR','CRA_MONITOR',"
                    + "'DATA_MANAGEMENT','FINANCE_MANAGER','QA_COMPLIANCE_AUDITOR','CLINICAL_LEADERSHIP',"
                    + "'EXECUTIVE','SPONSOR_CRO_LEADERSHIP')";

    private final DashboardService dashboardService;
    private final DashboardExportService dashboardExportService;

    public DashboardController(DashboardService dashboardService, DashboardExportService dashboardExportService) {
        this.dashboardService = dashboardService;
        this.dashboardExportService = dashboardExportService;
    }

    @GetMapping("/summary")
    @PreAuthorize(READ_ROLES)
    public DashboardSummaryResponse summary(
            Principal principal,
            @RequestParam(required = false) Long studyId,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String phase) {
        return dashboardService.summary(studyId, country, siteId, phase, principal.getName());
    }

    @GetMapping("/filter-options")
    @PreAuthorize(READ_ROLES)
    public DashboardFilterOptionsResponse filterOptions() {
        return dashboardService.filterOptions();
    }

    @GetMapping("/export")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<ByteArrayResource> export(
            Principal principal,
            @RequestParam(required = false) Long studyId,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String phase,
            @RequestParam String format) {
        byte[] content = dashboardExportService.export(studyId, country, siteId, phase, principal.getName(), format);
        boolean excel = "excel".equalsIgnoreCase(format);
        String fileName = "dashboard-report." + (excel ? "xlsx" : "pdf");
        MediaType mediaType = excel
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.APPLICATION_PDF;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(mediaType)
                .body(new ByteArrayResource(content));
    }
}

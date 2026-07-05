package com.ctms.ctms_backend.monitoring.controller;

import com.ctms.ctms_backend.monitoring.dto.LogMonitoringVisitRequest;
import com.ctms.ctms_backend.monitoring.dto.MonitoringVisitResponse;
import com.ctms.ctms_backend.monitoring.service.MonitoringVisitService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring-visits")
public class MonitoringVisitController {

    private static final String WRITE_ROLES = "hasAnyRole('CRA_MONITOR','ADMIN')";
    private static final String READ_ROLES =
            "hasAnyRole('STUDY_MANAGER','ADMIN','SITE_COORDINATOR','INVESTIGATOR','CRA_MONITOR',"
                    + "'DATA_MANAGEMENT','FINANCE_MANAGER','QA_COMPLIANCE_AUDITOR','CLINICAL_LEADERSHIP',"
                    + "'EXECUTIVE','SPONSOR_CRO_LEADERSHIP')";

    private final MonitoringVisitService monitoringVisitService;

    public MonitoringVisitController(MonitoringVisitService monitoringVisitService) {
        this.monitoringVisitService = monitoringVisitService;
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public MonitoringVisitResponse log(Principal principal, @Valid @RequestBody LogMonitoringVisitRequest req) {
        return monitoringVisitService.log(req, principal.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public MonitoringVisitResponse update(
            Principal principal, @PathVariable Long id, @Valid @RequestBody LogMonitoringVisitRequest req) {
        return monitoringVisitService.update(id, req, principal.getName());
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<MonitoringVisitResponse> list(@RequestParam Long siteId) {
        return monitoringVisitService.list(siteId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public MonitoringVisitResponse get(@PathVariable Long id) {
        return monitoringVisitService.get(id);
    }
}

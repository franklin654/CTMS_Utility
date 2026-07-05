package com.ctms.ctms_backend.visit.controller;

import com.ctms.ctms_backend.visit.dto.CreateAdHocVisitRequest;
import com.ctms.ctms_backend.visit.dto.MarkVisitCompletedRequest;
import com.ctms.ctms_backend.visit.dto.MarkVisitMissedRequest;
import com.ctms.ctms_backend.visit.dto.RescheduleVisitRequest;
import com.ctms.ctms_backend.visit.dto.SubjectVisitScheduleResponse;
import com.ctms.ctms_backend.visit.dto.VisitResponse;
import com.ctms.ctms_backend.visit.service.VisitService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VisitController {

    private static final String WRITE_ROLES = "hasAnyRole('SITE_COORDINATOR','STUDY_MANAGER','ADMIN')";
    /** Ad-hoc visit requests are often clinically motivated (AE follow-up, dose-modification
     * check), so Investigator can request one even though they can't act on protocol visits. */
    private static final String AD_HOC_WRITE_ROLES = "hasAnyRole('SITE_COORDINATOR','INVESTIGATOR','STUDY_MANAGER','ADMIN')";
    private static final String READ_ROLES =
            "hasAnyRole('STUDY_MANAGER','ADMIN','SITE_COORDINATOR','INVESTIGATOR','CRA_MONITOR',"
                    + "'DATA_MANAGEMENT','FINANCE_MANAGER','QA_COMPLIANCE_AUDITOR','CLINICAL_LEADERSHIP',"
                    + "'EXECUTIVE','SPONSOR_CRO_LEADERSHIP')";

    private final VisitService visitService;

    public VisitController(VisitService visitService) {
        this.visitService = visitService;
    }

    @GetMapping("/api/subjects/{subjectId}/visits")
    @PreAuthorize(READ_ROLES)
    public SubjectVisitScheduleResponse schedule(@PathVariable Long subjectId) {
        return visitService.schedule(subjectId);
    }

    @PostMapping("/api/subjects/{subjectId}/visits/ad-hoc")
    @PreAuthorize(AD_HOC_WRITE_ROLES)
    public VisitResponse scheduleAdHoc(
            Principal principal, @PathVariable Long subjectId, @Valid @RequestBody CreateAdHocVisitRequest req) {
        return visitService.scheduleAdHoc(subjectId, req, principal.getName());
    }

    @PostMapping("/api/visits/{id}/complete")
    @PreAuthorize(WRITE_ROLES)
    public VisitResponse complete(@PathVariable Long id, @Valid @RequestBody MarkVisitCompletedRequest req) {
        return visitService.markCompleted(id, req);
    }

    @PostMapping("/api/visits/{id}/miss")
    @PreAuthorize(WRITE_ROLES)
    public VisitResponse miss(@PathVariable Long id, @Valid @RequestBody MarkVisitMissedRequest req) {
        return visitService.markMissed(id, req);
    }

    @PostMapping("/api/visits/{id}/reschedule")
    @PreAuthorize(WRITE_ROLES)
    public VisitResponse reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleVisitRequest req) {
        return visitService.reschedule(id, req);
    }
}

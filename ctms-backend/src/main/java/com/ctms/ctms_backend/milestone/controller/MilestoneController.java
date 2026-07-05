package com.ctms.ctms_backend.milestone.controller;

import com.ctms.ctms_backend.milestone.dto.CreateMilestoneRequest;
import com.ctms.ctms_backend.milestone.dto.MilestoneResponse;
import com.ctms.ctms_backend.milestone.dto.RecordMilestoneActualRequest;
import com.ctms.ctms_backend.milestone.dto.UpdateMilestonePlannedDateRequest;
import com.ctms.ctms_backend.milestone.service.MilestoneService;
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
@RequestMapping("/api/milestones")
public class MilestoneController {

    private static final String WRITE_ROLES = "hasAnyRole('STUDY_MANAGER','ADMIN')";
    private static final String READ_ROLES =
            "hasAnyRole('STUDY_MANAGER','ADMIN','SITE_COORDINATOR','INVESTIGATOR','CRA_MONITOR',"
                    + "'DATA_MANAGEMENT','FINANCE_MANAGER','QA_COMPLIANCE_AUDITOR','CLINICAL_LEADERSHIP',"
                    + "'EXECUTIVE','SPONSOR_CRO_LEADERSHIP')";

    private final MilestoneService milestoneService;

    public MilestoneController(MilestoneService milestoneService) {
        this.milestoneService = milestoneService;
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public MilestoneResponse create(Principal principal, @Valid @RequestBody CreateMilestoneRequest req) {
        return milestoneService.create(req, principal.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public MilestoneResponse updatePlannedDate(
            Principal principal, @PathVariable Long id, @Valid @RequestBody UpdateMilestonePlannedDateRequest req) {
        return milestoneService.updatePlannedDate(id, req, principal.getName());
    }

    @PostMapping("/{id}/record-actual")
    @PreAuthorize(WRITE_ROLES)
    public MilestoneResponse recordActual(
            Principal principal, @PathVariable Long id, @Valid @RequestBody RecordMilestoneActualRequest req) {
        return milestoneService.recordActual(id, req, principal.getName());
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<MilestoneResponse> listByStudy(@RequestParam Long studyId) {
        return milestoneService.listByStudy(studyId);
    }
}

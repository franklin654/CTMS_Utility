package com.ctms.ctms_backend.deviation.controller;

import com.ctms.ctms_backend.deviation.dto.ProtocolDeviationResponse;
import com.ctms.ctms_backend.deviation.dto.ReportProtocolDeviationRequest;
import com.ctms.ctms_backend.deviation.service.ProtocolDeviationService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** BL Epic 11 Story 01 (protocol deviation logging). RBAC mirrors AdverseEventController exactly. */
@RestController
@RequestMapping("/api/protocol-deviations")
public class ProtocolDeviationController {

    private static final String REPORT_ROLES = "hasAnyRole('SITE_COORDINATOR','INVESTIGATOR','ADMIN')";
    private static final String READ_ROLES =
            "hasAnyRole('STUDY_MANAGER','ADMIN','SITE_COORDINATOR','INVESTIGATOR','CRA_MONITOR',"
                    + "'DATA_MANAGEMENT','FINANCE_MANAGER','QA_COMPLIANCE_AUDITOR','CLINICAL_LEADERSHIP',"
                    + "'EXECUTIVE','SPONSOR_CRO_LEADERSHIP')";

    private final ProtocolDeviationService protocolDeviationService;

    public ProtocolDeviationController(ProtocolDeviationService protocolDeviationService) {
        this.protocolDeviationService = protocolDeviationService;
    }

    @PostMapping
    @PreAuthorize(REPORT_ROLES)
    public ProtocolDeviationResponse report(Principal principal, @Valid @RequestBody ReportProtocolDeviationRequest req) {
        return protocolDeviationService.report(req, principal.getName());
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<ProtocolDeviationResponse> list(@RequestParam Long subjectId) {
        return protocolDeviationService.list(subjectId);
    }
}

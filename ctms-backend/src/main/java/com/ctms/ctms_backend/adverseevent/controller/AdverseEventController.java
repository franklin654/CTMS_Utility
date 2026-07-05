package com.ctms.ctms_backend.adverseevent.controller;

import com.ctms.ctms_backend.adverseevent.dto.AdverseEventResponse;
import com.ctms.ctms_backend.adverseevent.dto.ReportAdverseEventRequest;
import com.ctms.ctms_backend.adverseevent.dto.ResolveAdverseEventRequest;
import com.ctms.ctms_backend.adverseevent.dto.TransitionAdverseEventRequest;
import com.ctms.ctms_backend.adverseevent.service.AdverseEventService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/adverse-events")
public class AdverseEventController {

    /** Participant-side reporting is Phase 11's Patient Portal -- staff-side this phase is
     * scoped to Investigator/Site Coordinator per the implementation plan's own note. */
    private static final String REPORT_ROLES = "hasAnyRole('SITE_COORDINATOR','INVESTIGATOR','ADMIN')";
    /** Status-transition/resolution is a clinical judgment call, not a coordinator action. */
    private static final String REVIEW_ROLES = "hasAnyRole('INVESTIGATOR','STUDY_MANAGER','ADMIN')";
    private static final String READ_ROLES =
            "hasAnyRole('STUDY_MANAGER','ADMIN','SITE_COORDINATOR','INVESTIGATOR','CRA_MONITOR',"
                    + "'DATA_MANAGEMENT','FINANCE_MANAGER','QA_COMPLIANCE_AUDITOR','CLINICAL_LEADERSHIP',"
                    + "'EXECUTIVE','SPONSOR_CRO_LEADERSHIP')";
    private static final String BOARD_ROLES = "hasAnyRole('INVESTIGATOR','STUDY_MANAGER','ADMIN','CRA_MONITOR')";

    private final AdverseEventService adverseEventService;

    public AdverseEventController(AdverseEventService adverseEventService) {
        this.adverseEventService = adverseEventService;
    }

    @PostMapping
    @PreAuthorize(REPORT_ROLES)
    public AdverseEventResponse report(Principal principal, @Valid @RequestBody ReportAdverseEventRequest req) {
        return adverseEventService.report(req, principal.getName());
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize(REVIEW_ROLES)
    public AdverseEventResponse transition(
            Principal principal, @PathVariable Long id, @Valid @RequestBody TransitionAdverseEventRequest req) {
        return adverseEventService.transition(id, req, principal.getName());
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize(REVIEW_ROLES)
    public AdverseEventResponse resolve(
            Principal principal, @PathVariable Long id, @Valid @RequestBody ResolveAdverseEventRequest req) {
        return adverseEventService.resolve(id, req, principal.getName());
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<AdverseEventResponse> list(@RequestParam Long subjectId) {
        return adverseEventService.list(subjectId);
    }

    @GetMapping("/board")
    @PreAuthorize(BOARD_ROLES)
    public List<AdverseEventResponse> board() {
        return adverseEventService.board();
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public AdverseEventResponse get(@PathVariable Long id) {
        return adverseEventService.get(id);
    }
}

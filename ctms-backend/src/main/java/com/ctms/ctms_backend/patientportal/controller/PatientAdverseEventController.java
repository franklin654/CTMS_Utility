package com.ctms.ctms_backend.patientportal.controller;

import com.ctms.ctms_backend.adverseevent.dto.AdverseEventResponse;
import com.ctms.ctms_backend.adverseevent.dto.ReportAdverseEventRequest;
import com.ctms.ctms_backend.adverseevent.service.AdverseEventService;
import com.ctms.ctms_backend.patientportal.dto.PatientReportAdverseEventRequest;
import com.ctms.ctms_backend.patientportal.service.PatientContextService;
import com.ctms.ctms_backend.subject.entity.Subject;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Patient self-reporting, closing the gap Phase 7 explicitly deferred to this phase (per your
 * scope decision). Reuses Phase 7's AdverseEventService.report unchanged -- lands in the same
 * OPEN state staff already monitor via the existing AE board, no changes needed there. */
@RestController
@RequestMapping("/api/patient/adverse-events")
@PreAuthorize("hasRole('PATIENT_SUBJECT')")
public class PatientAdverseEventController {

    private final PatientContextService patientContextService;
    private final AdverseEventService adverseEventService;

    public PatientAdverseEventController(PatientContextService patientContextService, AdverseEventService adverseEventService) {
        this.patientContextService = patientContextService;
        this.adverseEventService = adverseEventService;
    }

    @GetMapping
    public List<AdverseEventResponse> list(Principal principal) {
        Subject subject = patientContextService.resolveCurrentSubject(principal.getName());
        return adverseEventService.list(subject.getId());
    }

    @PostMapping
    public AdverseEventResponse report(Principal principal, @Valid @RequestBody PatientReportAdverseEventRequest req) {
        Subject subject = patientContextService.resolveCurrentSubject(principal.getName());
        return adverseEventService.report(
                new ReportAdverseEventRequest(subject.getId(), null, req.description(), req.severity()), principal.getName());
    }
}

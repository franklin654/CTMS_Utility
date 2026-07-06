package com.ctms.ctms_backend.patientportal.controller;

import com.ctms.ctms_backend.patientportal.service.PatientContextService;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.visit.dto.SubjectVisitScheduleResponse;
import com.ctms.ctms_backend.visit.service.VisitService;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BL Epic 10 Story 02 (View Visit Schedule). Delegates straight to the existing
 * VisitService.schedule -- zero changes to Visit code needed, it already returns exactly what
 * Story 02 needs. The subject is always resolved server-side from the caller's own identity via
 * PatientContextService, never from a client-supplied ID -- this is what makes it structurally
 * impossible for a patient to see another subject's schedule. */
@RestController
@RequestMapping("/api/patient/visits")
@PreAuthorize("hasRole('PATIENT_SUBJECT')")
public class PatientVisitController {

    private final PatientContextService patientContextService;
    private final VisitService visitService;

    public PatientVisitController(PatientContextService patientContextService, VisitService visitService) {
        this.patientContextService = patientContextService;
        this.visitService = visitService;
    }

    @GetMapping
    public SubjectVisitScheduleResponse mySchedule(Principal principal) {
        Subject subject = patientContextService.resolveCurrentSubject(principal.getName());
        return visitService.schedule(subject.getId());
    }
}

package com.ctms.ctms_backend.patientportal.controller;

import com.ctms.ctms_backend.patientportal.service.PatientContextService;
import com.ctms.ctms_backend.subject.dto.SubjectResponse;
import com.ctms.ctms_backend.subject.dto.UpdateOwnProfileRequest;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.service.SubjectService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BL Epic 10 Story 05 (Update Patient Profile). */
@RestController
@RequestMapping("/api/patient/profile")
@PreAuthorize("hasRole('PATIENT_SUBJECT')")
public class PatientProfileController {

    private final PatientContextService patientContextService;
    private final SubjectService subjectService;

    public PatientProfileController(PatientContextService patientContextService, SubjectService subjectService) {
        this.patientContextService = patientContextService;
        this.subjectService = subjectService;
    }

    @GetMapping
    public SubjectResponse get(Principal principal) {
        Subject subject = patientContextService.resolveCurrentSubject(principal.getName());
        return subjectService.get(subject.getId());
    }

    @PutMapping
    public SubjectResponse update(Principal principal, @Valid @RequestBody UpdateOwnProfileRequest req) {
        Subject subject = patientContextService.resolveCurrentSubject(principal.getName());
        return subjectService.updateOwnProfile(subject.getId(), req, principal.getName());
    }
}

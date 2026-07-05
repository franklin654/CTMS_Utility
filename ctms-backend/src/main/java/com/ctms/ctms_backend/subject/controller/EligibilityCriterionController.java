package com.ctms.ctms_backend.subject.controller;

import com.ctms.ctms_backend.subject.dto.CreateEligibilityCriterionRequest;
import com.ctms.ctms_backend.subject.dto.EligibilityCriterionResponse;
import com.ctms.ctms_backend.subject.service.EligibilityCriterionService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/eligibility-criteria")
public class EligibilityCriterionController {

    /** Only Study Managers/Admins define the criteria; Site Coordinators (who actually enroll
     * subjects, per Story 01's actor) need read access to render the checklist at enrollment time. */
    private static final String WRITE_ROLES = "hasAnyRole('STUDY_MANAGER', 'ADMIN')";
    private static final String READ_ROLES = "hasAnyRole('STUDY_MANAGER', 'SITE_COORDINATOR', 'ADMIN')";

    private final EligibilityCriterionService criterionService;

    public EligibilityCriterionController(EligibilityCriterionService criterionService) {
        this.criterionService = criterionService;
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public EligibilityCriterionResponse create(@Valid @RequestBody CreateEligibilityCriterionRequest req) {
        return criterionService.create(req);
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<EligibilityCriterionResponse> list(@RequestParam Long studyId) {
        return criterionService.listActive(studyId);
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize(WRITE_ROLES)
    public EligibilityCriterionResponse deactivate(@PathVariable Long id) {
        return criterionService.deactivate(id);
    }
}

package com.ctms.ctms_backend.testresult.controller;

import com.ctms.ctms_backend.testresult.dto.CreateTestResultRequest;
import com.ctms.ctms_backend.testresult.dto.TestResultResponse;
import com.ctms.ctms_backend.testresult.service.TestResultService;
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
@RequestMapping("/api/test-results")
public class TestResultController {

    private static final String WRITE_ROLES = "hasAnyRole('SITE_COORDINATOR','INVESTIGATOR','STUDY_MANAGER','ADMIN')";
    /** Reviewing implies clinical judgment -- the BRD's literal "doctor" actor -- so Site
     * Coordinator can record but not review. */
    private static final String REVIEW_ROLES = "hasAnyRole('INVESTIGATOR','STUDY_MANAGER','ADMIN')";
    private static final String READ_ROLES =
            "hasAnyRole('STUDY_MANAGER','ADMIN','SITE_COORDINATOR','INVESTIGATOR','CRA_MONITOR',"
                    + "'DATA_MANAGEMENT','FINANCE_MANAGER','QA_COMPLIANCE_AUDITOR','CLINICAL_LEADERSHIP',"
                    + "'EXECUTIVE','SPONSOR_CRO_LEADERSHIP')";

    private final TestResultService testResultService;

    public TestResultController(TestResultService testResultService) {
        this.testResultService = testResultService;
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public TestResultResponse record(Principal principal, @Valid @RequestBody CreateTestResultRequest req) {
        return testResultService.record(req, principal.getName());
    }

    @PostMapping("/{id}/review")
    @PreAuthorize(REVIEW_ROLES)
    public TestResultResponse review(Principal principal, @PathVariable Long id) {
        return testResultService.review(id, principal.getName());
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<TestResultResponse> list(@RequestParam Long subjectId) {
        return testResultService.list(subjectId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public TestResultResponse get(@PathVariable Long id) {
        return testResultService.get(id);
    }
}

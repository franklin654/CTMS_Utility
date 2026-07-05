package com.ctms.ctms_backend.subject.controller;

import com.ctms.ctms_backend.subject.dto.EnrollSubjectRequest;
import com.ctms.ctms_backend.subject.dto.SubjectResponse;
import com.ctms.ctms_backend.subject.dto.SubjectStatusHistoryResponse;
import com.ctms.ctms_backend.subject.dto.TransitionSubjectRequest;
import com.ctms.ctms_backend.subject.dto.UpdateSubjectRequest;
import com.ctms.ctms_backend.subject.dto.WithdrawSubjectRequest;
import com.ctms.ctms_backend.subject.service.SubjectLifecycleService;
import com.ctms.ctms_backend.subject.service.SubjectService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/subjects")
public class SubjectController {

    private static final String WRITE_ROLES = "hasAnyRole('SITE_COORDINATOR','STUDY_MANAGER','ADMIN')";
    private static final String READ_ROLES =
            "hasAnyRole('STUDY_MANAGER','ADMIN','SITE_COORDINATOR','INVESTIGATOR','CRA_MONITOR',"
                    + "'DATA_MANAGEMENT','FINANCE_MANAGER','QA_COMPLIANCE_AUDITOR','CLINICAL_LEADERSHIP',"
                    + "'EXECUTIVE','SPONSOR_CRO_LEADERSHIP')";

    private final SubjectService subjectService;
    private final SubjectLifecycleService subjectLifecycleService;

    public SubjectController(SubjectService subjectService, SubjectLifecycleService subjectLifecycleService) {
        this.subjectService = subjectService;
        this.subjectLifecycleService = subjectLifecycleService;
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public SubjectResponse enroll(Principal principal, @Valid @RequestBody EnrollSubjectRequest req) {
        return subjectService.enrollSubject(req, principal.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public SubjectResponse update(Principal principal, @PathVariable Long id, @Valid @RequestBody UpdateSubjectRequest req) {
        return subjectService.updateSubject(id, req, principal.getName());
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public SubjectResponse get(@PathVariable Long id) {
        return subjectService.get(id);
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public Page<SubjectResponse> list(
            @RequestParam(required = false) Long studyId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return subjectService.list(studyId, siteId, search, pageable);
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize(WRITE_ROLES)
    public SubjectResponse transition(Principal principal, @PathVariable Long id, @Valid @RequestBody TransitionSubjectRequest req) {
        return subjectLifecycleService.transition(id, req, principal.getName());
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize(WRITE_ROLES)
    public SubjectResponse withdraw(Principal principal, @PathVariable Long id, @Valid @RequestBody WithdrawSubjectRequest req) {
        return subjectLifecycleService.withdraw(id, req, principal.getName());
    }

    @GetMapping("/{id}/history")
    @PreAuthorize(READ_ROLES)
    public List<SubjectStatusHistoryResponse> history(@PathVariable Long id) {
        return subjectLifecycleService.history(id);
    }
}

package com.ctms.ctms_backend.study.controller;

import com.ctms.ctms_backend.study.dto.CloseoutStudyRequest;
import com.ctms.ctms_backend.study.dto.CreateStudyRequest;
import com.ctms.ctms_backend.study.dto.StudyResponse;
import com.ctms.ctms_backend.study.dto.StudyStatusHistoryResponse;
import com.ctms.ctms_backend.study.dto.TransitionStudyRequest;
import com.ctms.ctms_backend.study.dto.UpdateStudyRequest;
import com.ctms.ctms_backend.study.service.StudyService;
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
@RequestMapping("/api/studies")
public class StudyController {

    private static final String READ_ROLES =
            "hasAnyRole('STUDY_MANAGER','ADMIN','SITE_COORDINATOR','INVESTIGATOR','CRA_MONITOR',"
                    + "'DATA_MANAGEMENT','FINANCE_MANAGER','QA_COMPLIANCE_AUDITOR','CLINICAL_LEADERSHIP',"
                    + "'EXECUTIVE','SPONSOR_CRO_LEADERSHIP')";
    private static final String WRITE_ROLES = "hasAnyRole('STUDY_MANAGER','ADMIN')";

    private final StudyService studyService;

    public StudyController(StudyService studyService) {
        this.studyService = studyService;
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public StudyResponse create(Principal principal, @Valid @RequestBody CreateStudyRequest req) {
        return studyService.createStudy(req, principal.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public StudyResponse update(Principal principal, @PathVariable Long id, @Valid @RequestBody UpdateStudyRequest req) {
        return studyService.updateStudy(id, req, principal.getName());
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize(WRITE_ROLES)
    public StudyResponse transition(Principal principal, @PathVariable Long id, @Valid @RequestBody TransitionStudyRequest req) {
        return studyService.transition(id, req, principal.getName());
    }

    @PostMapping("/{id}/closeout")
    @PreAuthorize(WRITE_ROLES)
    public StudyResponse closeout(Principal principal, @PathVariable Long id, @Valid @RequestBody CloseoutStudyRequest req) {
        return studyService.closeout(id, req, principal.getName());
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public StudyResponse get(@PathVariable Long id) {
        return studyService.get(id);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize(READ_ROLES)
    public List<StudyStatusHistoryResponse> history(@PathVariable Long id) {
        return studyService.history(id);
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public Page<StudyResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return studyService.list(search, pageable);
    }
}

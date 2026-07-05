package com.ctms.ctms_backend.visit.controller;

import com.ctms.ctms_backend.visit.dto.CreateVisitTemplateRequest;
import com.ctms.ctms_backend.visit.dto.UpdateVisitTemplateRequest;
import com.ctms.ctms_backend.visit.dto.VisitTemplateResponse;
import com.ctms.ctms_backend.visit.service.VisitTemplateService;
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
@RequestMapping("/api/visit-templates")
public class VisitTemplateController {

    private static final String WRITE_ROLES = "hasAnyRole('STUDY_MANAGER','ADMIN')";
    private static final String READ_ROLES = "hasAnyRole('STUDY_MANAGER','SITE_COORDINATOR','ADMIN')";

    private final VisitTemplateService templateService;

    public VisitTemplateController(VisitTemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public VisitTemplateResponse create(Principal principal, @Valid @RequestBody CreateVisitTemplateRequest req) {
        return templateService.create(req, principal.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public VisitTemplateResponse update(
            Principal principal, @PathVariable Long id, @Valid @RequestBody UpdateVisitTemplateRequest req) {
        return templateService.update(id, req, principal.getName());
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<VisitTemplateResponse> list(@RequestParam Long studyId) {
        return templateService.list(studyId);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(WRITE_ROLES)
    public VisitTemplateResponse deactivate(Principal principal, @PathVariable Long id) {
        return templateService.deactivate(id, principal.getName());
    }
}

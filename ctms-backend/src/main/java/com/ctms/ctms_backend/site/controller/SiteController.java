package com.ctms.ctms_backend.site.controller;

import com.ctms.ctms_backend.site.dto.ActivationAttemptResponse;
import com.ctms.ctms_backend.site.dto.AssignCraRequest;
import com.ctms.ctms_backend.site.dto.ChecklistItemResponse;
import com.ctms.ctms_backend.site.dto.CreateSiteRequest;
import com.ctms.ctms_backend.site.dto.SiteResponse;
import com.ctms.ctms_backend.site.dto.UpdateChecklistItemRequest;
import com.ctms.ctms_backend.site.dto.UpdateSiteRequest;
import com.ctms.ctms_backend.site.service.SiteActivationService;
import com.ctms.ctms_backend.site.service.SiteService;
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
@RequestMapping("/api/sites")
public class SiteController {

    private static final String READ_ROLES =
            "hasAnyRole('STUDY_MANAGER','ADMIN','SITE_COORDINATOR','INVESTIGATOR','CRA_MONITOR',"
                    + "'DATA_MANAGEMENT','FINANCE_MANAGER','QA_COMPLIANCE_AUDITOR','CLINICAL_LEADERSHIP',"
                    + "'EXECUTIVE','SPONSOR_CRO_LEADERSHIP')";
    private static final String WRITE_ROLES = "hasAnyRole('STUDY_MANAGER','ADMIN')";

    private final SiteService siteService;
    private final SiteActivationService siteActivationService;

    public SiteController(SiteService siteService, SiteActivationService siteActivationService) {
        this.siteService = siteService;
        this.siteActivationService = siteActivationService;
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public SiteResponse register(Principal principal, @Valid @RequestBody CreateSiteRequest req) {
        return siteService.registerSite(req, principal.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public SiteResponse update(Principal principal, @PathVariable Long id, @Valid @RequestBody UpdateSiteRequest req) {
        return siteService.updateSite(id, req, principal.getName());
    }

    @PutMapping("/{id}/cra")
    @PreAuthorize(WRITE_ROLES)
    public SiteResponse assignCra(Principal principal, @PathVariable Long id, @Valid @RequestBody AssignCraRequest req) {
        return siteService.assignCra(id, req, principal.getName());
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public SiteResponse get(@PathVariable Long id) {
        return siteService.get(id);
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public Page<SiteResponse> list(
            @RequestParam(required = false) Long studyId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return siteService.list(studyId, search, pageable);
    }

    @GetMapping("/{id}/checklist")
    @PreAuthorize(READ_ROLES)
    public List<ChecklistItemResponse> checklist(@PathVariable Long id) {
        return siteActivationService.checklist(id);
    }

    @PutMapping("/{id}/checklist/{itemType}")
    @PreAuthorize(WRITE_ROLES)
    public ChecklistItemResponse updateChecklistItem(
            Principal principal,
            @PathVariable Long id,
            @PathVariable String itemType,
            @Valid @RequestBody UpdateChecklistItemRequest req) {
        return siteActivationService.updateChecklistItem(id, itemType, req, principal.getName());
    }

    @PostMapping("/{id}/attempt-activation")
    @PreAuthorize(WRITE_ROLES)
    public ActivationAttemptResponse attemptActivation(Principal principal, @PathVariable Long id) {
        return siteActivationService.attemptActivation(id, principal.getName());
    }
}

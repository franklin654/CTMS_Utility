package com.ctms.ctms_backend.document.controller;

import com.ctms.ctms_backend.document.dto.CreateDocumentRequirementRequest;
import com.ctms.ctms_backend.document.dto.DocumentRequirementResponse;
import com.ctms.ctms_backend.document.service.DocumentRequirementService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** BL Epic 9 Story 03. Writes are Admin-only (System Configuration surface); reads are broad
 * (all non-patient operational roles) -- knowing what's required is useful operational info, not
 * sensitive like Phase 9's financial data. */
@RestController
@RequestMapping("/api/document-requirements")
public class DocumentRequirementController {

    private static final String READ_ROLES =
            "hasAnyRole('STUDY_MANAGER','SITE_COORDINATOR','ADMIN','INVESTIGATOR','CRA_MONITOR',"
                    + "'DATA_MANAGEMENT','FINANCE_MANAGER','QA_COMPLIANCE_AUDITOR','CLINICAL_LEADERSHIP',"
                    + "'EXECUTIVE','SPONSOR_CRO_LEADERSHIP')";

    private final DocumentRequirementService documentRequirementService;

    public DocumentRequirementController(DocumentRequirementService documentRequirementService) {
        this.documentRequirementService = documentRequirementService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public DocumentRequirementResponse create(Principal principal, @Valid @RequestBody CreateDocumentRequirementRequest request) {
        return documentRequirementService.create(request, principal.getName());
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<DocumentRequirementResponse> listByStudy(@RequestParam Long studyId) {
        return documentRequirementService.listByStudy(studyId);
    }
}

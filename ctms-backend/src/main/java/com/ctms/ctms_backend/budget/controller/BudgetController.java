package com.ctms.ctms_backend.budget.controller;

import com.ctms.ctms_backend.budget.dto.BudgetVersionResponse;
import com.ctms.ctms_backend.budget.dto.CreateBudgetRequest;
import com.ctms.ctms_backend.budget.dto.CreateBudgetVersionRequest;
import com.ctms.ctms_backend.budget.service.BudgetExportService;
import com.ctms.ctms_backend.budget.service.BudgetService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** BL Epic 8 Story 02/03. Every endpoint here is Finance Manager/Admin only -- per CLAUDE.md's
 * own "CRAs can't see financials" precedent (Epic 7 Story 05), this is the first phase with
 * genuinely restricted read access rather than the broad-read default every prior phase used. */
@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private static final String ROLES = "hasAnyRole('FINANCE_MANAGER','ADMIN')";

    private final BudgetService budgetService;
    private final BudgetExportService budgetExportService;

    public BudgetController(BudgetService budgetService, BudgetExportService budgetExportService) {
        this.budgetService = budgetService;
        this.budgetExportService = budgetExportService;
    }

    @PostMapping
    @PreAuthorize(ROLES)
    public BudgetVersionResponse create(Principal principal, @Valid @RequestBody CreateBudgetRequest req) {
        return budgetService.create(req, principal.getName());
    }

    @PostMapping("/{studyId}/versions")
    @PreAuthorize(ROLES)
    public BudgetVersionResponse createNewVersion(
            Principal principal, @PathVariable Long studyId, @Valid @RequestBody CreateBudgetVersionRequest req) {
        return budgetService.createNewVersion(studyId, req, principal.getName());
    }

    @GetMapping("/{studyId}")
    @PreAuthorize(ROLES)
    public BudgetVersionResponse getCurrentVersion(@PathVariable Long studyId) {
        return budgetService.getCurrentVersion(studyId);
    }

    @GetMapping("/{studyId}/versions")
    @PreAuthorize(ROLES)
    public List<BudgetVersionResponse> listVersions(@PathVariable Long studyId) {
        return budgetService.listVersions(studyId);
    }

    @GetMapping("/{studyId}/versions/{versionNumber}")
    @PreAuthorize(ROLES)
    public BudgetVersionResponse getVersion(@PathVariable Long studyId, @PathVariable int versionNumber) {
        return budgetService.getVersion(studyId, versionNumber);
    }

    @GetMapping("/{studyId}/export")
    @PreAuthorize(ROLES)
    public ResponseEntity<ByteArrayResource> export(@PathVariable Long studyId, @RequestParam String format) {
        byte[] content = budgetExportService.export(studyId, format);
        boolean excel = "excel".equalsIgnoreCase(format);
        String fileName = "budget-report." + (excel ? "xlsx" : "pdf");
        MediaType mediaType = excel
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.APPLICATION_PDF;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(mediaType)
                .body(new ByteArrayResource(content));
    }
}

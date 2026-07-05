package com.ctms.ctms_backend.document.controller;

import com.ctms.ctms_backend.document.DocumentVersionResponse;
import com.ctms.ctms_backend.document.dto.DocumentReviewResponse;
import com.ctms.ctms_backend.document.dto.FinalApprovalRequest;
import com.ctms.ctms_backend.document.dto.ReviewDecisionRequest;
import com.ctms.ctms_backend.document.entity.ReviewStage;
import com.ctms.ctms_backend.document.service.DocumentWorkflowService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Document approval-workflow actions -- kept separate from the read/upload {@code DocumentController}
 * so its distinct reviewer/approver RBAC surface stays visually separate. @PreAuthorize here is
 * intentionally coarse ("could ever review/approve anything"); DocumentWorkflowService re-checks
 * the actual data-driven role for each document's category before acting.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentApprovalController {

    private static final String WRITE_ROLES = "hasAnyRole('STUDY_MANAGER','SITE_COORDINATOR','ADMIN')";
    private static final String WORKFLOW_ROLES = "hasAnyRole('STUDY_MANAGER','QA_COMPLIANCE_AUDITOR','ADMIN')";

    private final DocumentWorkflowService workflowService;

    public DocumentApprovalController(DocumentWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping("/{id}/versions/{versionNumber}/submit")
    @PreAuthorize(WRITE_ROLES)
    public DocumentReviewResponse submit(Principal principal, @PathVariable Long id, @PathVariable int versionNumber) {
        return workflowService.submitForReview(id, versionNumber, principal.getName());
    }

    @PostMapping("/{id}/versions/{versionNumber}/review")
    @PreAuthorize(WORKFLOW_ROLES)
    public DocumentReviewResponse review(
            Principal principal, @PathVariable Long id, @PathVariable int versionNumber,
            @Valid @RequestBody ReviewDecisionRequest req) {
        return workflowService.reviewerDecide(id, versionNumber, req, principal.getName());
    }

    @PostMapping("/{id}/versions/{versionNumber}/approve")
    @PreAuthorize(WORKFLOW_ROLES)
    public DocumentReviewResponse approve(
            Principal principal, @PathVariable Long id, @PathVariable int versionNumber,
            @Valid @RequestBody FinalApprovalRequest req) {
        return workflowService.approverFinalDecide(id, versionNumber, req, principal.getName());
    }

    @GetMapping("/{id}/versions/{versionNumber}/reviews")
    @PreAuthorize(WORKFLOW_ROLES)
    public List<DocumentReviewResponse> history(@PathVariable Long id, @PathVariable int versionNumber) {
        return workflowService.history(id, versionNumber);
    }

    @GetMapping("/approval-queue")
    @PreAuthorize(WORKFLOW_ROLES)
    public Page<DocumentVersionResponse> approvalQueue(
            @RequestParam String stage,
            @PageableDefault(size = 20) Pageable pageable) {
        return workflowService.approvalQueue(ReviewStage.valueOf(stage), pageable);
    }
}

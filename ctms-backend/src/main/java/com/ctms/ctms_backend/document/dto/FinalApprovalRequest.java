package com.ctms.ctms_backend.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** For the APPROVAL stage. action in {APPROVED, REJECTED}. password+reason are required when
 * action == APPROVED (drives ESignatureService.sign); comment is required when action == REJECTED.
 * Cross-field enforcement lives in DocumentWorkflowService. */
public record FinalApprovalRequest(
        @NotBlank String action, @Size(max = 2000) String comment, String password, String reason) {}

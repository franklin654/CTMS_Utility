package com.ctms.ctms_backend.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** For the REVIEW stage: action in {APPROVED, REJECTED, CHANGES_REQUESTED}. comment is required
 * unless action == APPROVED (enforced in DocumentWorkflowService, a cross-field rule not
 * expressible as a single Bean Validation annotation without a custom validator). */
public record ReviewDecisionRequest(@NotBlank String action, @Size(max = 2000) String comment) {}
